package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDefinitionCacheTest {

    @Test
    void returnsDeepDefensiveCopiesForCacheHit() {
        ProcessDefinitionCache cache = new ProcessDefinitionCache();
        ProcessDefinitionDetailDTO source = publishedDefinition("definition-1", 1);

        assertTrue(cache.put(source, validResult(), cache.captureGeneration("definition-1")));
        source.setProcessName("changed-source");
        source.getNodes().get(0).setNodeCode("changed-source-node");
        source.getAttachmentTemplates().get(0).getAllowedExtensions().add("exe");

        ProcessDefinitionDetailDTO firstRead = cache.get("definition-1", 1);
        assertEquals("Expense approval", firstRead.getProcessName());
        assertEquals("review", firstRead.getNodes().get(0).getNodeCode());
        assertEquals(Arrays.asList("pdf", "png"),
                firstRead.getAttachmentTemplates().get(0).getAllowedExtensions());
        assertNotSame(source.getNodes().get(0), firstRead.getNodes().get(0));

        firstRead.getNodes().get(0).setNodeCode("changed-read-node");
        firstRead.getFormFields().clear();
        firstRead.getAttachmentTemplates().get(0).getApplicableNodeCodes().clear();

        ProcessDefinitionDetailDTO secondRead = cache.get("definition-1", 1);
        assertEquals("review", secondRead.getNodes().get(0).getNodeCode());
        assertEquals(1, secondRead.getFormFields().size());
        assertEquals(Collections.singletonList("review"),
                secondRead.getAttachmentTemplates().get(0).getApplicableNodeCodes());
        assertNotSame(firstRead, secondRead);
    }

    @Test
    void isolatesVersionsAndInvalidatesAllVersionsForDefinition() {
        ProcessDefinitionCache cache = new ProcessDefinitionCache();
        ProcessDefinitionDetailDTO versionOne = publishedDefinition("definition-1", 1);
        ProcessDefinitionDetailDTO versionTwo = publishedDefinition("definition-1", 2);
        versionTwo.setProcessName("Expense approval v2");

        assertNull(cache.get("definition-1", 3));
        assertTrue(cache.put(versionOne, validResult(), cache.captureGeneration("definition-1")));
        assertTrue(cache.put(versionTwo, validResult(), cache.captureGeneration("definition-1")));
        assertEquals("Expense approval", cache.get("definition-1", 1).getProcessName());
        assertEquals("Expense approval v2", cache.get("definition-1", 2).getProcessName());

        cache.invalidate("definition-1");

        assertNull(cache.get("definition-1", 1));
        assertNull(cache.get("definition-1", 2));
    }

    @Test
    void rejectsNonCacheableDefinitionsWithoutEvictingActiveVersion() {
        ProcessDefinitionCache cache = new ProcessDefinitionCache();
        ProcessDefinitionDetailDTO active = publishedDefinition("definition-1", 1);
        ProcessDefinitionDetailDTO draft = publishedDefinition("definition-1", 2);
        draft.setDefinitionStatus(DefinitionStatusEnum.DRAFT);
        draft.setActivationStatus(ActivationStatusEnum.INACTIVE);
        ProcessDefinitionDetailDTO inactive = publishedDefinition("definition-1", 3);
        inactive.setActivationStatus(ActivationStatusEnum.INACTIVE);
        ProcessDefinitionDetailDTO invalid = publishedDefinition("definition-1", 4);
        ProcessDefinitionDetailDTO missingId = publishedDefinition(" ", 5);
        ProcessDefinitionDetailDTO missingVersion = publishedDefinition("definition-1", null);

        assertTrue(cache.put(active, validResult(), cache.captureGeneration("definition-1")));
        assertFalse(cache.put(draft, validResult(), cache.captureGeneration("definition-1")));
        assertFalse(cache.put(inactive, validResult(), cache.captureGeneration("definition-1")));
        assertFalse(cache.put(invalid, invalidResult(), cache.captureGeneration("definition-1")));
        assertFalse(cache.put(missingId, validResult(), cache.captureGeneration(" ")));
        assertFalse(cache.put(missingVersion, validResult(), cache.captureGeneration("definition-1")));
        assertFalse(cache.put(null, validResult(), null));
        assertFalse(cache.put(active, null, cache.captureGeneration("definition-1")));

        assertEquals("Expense approval", cache.get("definition-1", 1).getProcessName());
        assertNull(cache.get("definition-1", 2));
        assertNull(cache.get("definition-1", 3));
        assertNull(cache.get("definition-1", 4));
    }

    @Test
    void preservesNullDefinitionCollectionsInCopies() {
        ProcessDefinitionCache cache = new ProcessDefinitionCache();
        ProcessDefinitionDetailDTO source = publishedDefinition("definition-1", 1);
        source.setNodes(null);
        source.setEdges(null);
        source.setFormFields(null);
        source.setAttachmentTemplates(null);

        assertTrue(cache.put(source, validResult(), cache.captureGeneration("definition-1")));
        ProcessDefinitionDetailDTO cached = cache.get("definition-1", 1);

        assertNull(cached.getNodes());
        assertNull(cached.getEdges());
        assertNull(cached.getFormFields());
        assertNull(cached.getAttachmentTemplates());
    }

    @Test
    void supportsConcurrentIndependentPutsAndGets() throws Exception {
        final ProcessDefinitionCache cache = new ProcessDefinitionCache();
        ExecutorService executor = Executors.newFixedThreadPool(18);
        CountDownLatch ready = new CountDownLatch(18);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
        try {
            for (int version = 1; version <= 18; version++) {
                final int currentVersion = version;
                futures.add(executor.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            return false;
                        }
                        ProcessDefinitionDetailDTO definition =
                                publishedDefinition("definition-concurrent", currentVersion);
                        return cache.put(definition, validResult(),
                                cache.captureGeneration("definition-concurrent"))
                                && cache.get("definition-concurrent", currentVersion) != null;
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectsLoadResultCapturedBeforeInvalidation() {
        ProcessDefinitionCache cache = new ProcessDefinitionCache();
        ProcessDefinitionDetailDTO staleDefinition = publishedDefinition("definition-1", 1);
        ProcessDefinitionCache.CacheGeneration staleGeneration =
                cache.captureGeneration("definition-1");

        cache.invalidate("definition-1");

        assertFalse(cache.put(staleDefinition, validResult(), staleGeneration));
        assertNull(cache.get("definition-1", 1));
        assertTrue(cache.put(staleDefinition, validResult(),
                cache.captureGeneration("definition-1")));
    }

    @Test
    void rejectsInFlightStalePutAfterInvalidation() throws Exception {
        ProcessDefinitionCache cache = new ProcessDefinitionCache();
        CountDownLatch copyStarted = new CountDownLatch(1);
        CountDownLatch continueCopy = new CountDownLatch(1);
        ProcessDefinitionDetailDTO staleDefinition =
                blockingPublishedDefinition("definition-1", 1, copyStarted, continueCopy);
        ProcessDefinitionCache.CacheGeneration staleGeneration =
                cache.captureGeneration("definition-1");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> stalePut = executor.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return cache.put(staleDefinition, validResult(), staleGeneration);
                }
            });

            assertTrue(copyStarted.await(5, TimeUnit.SECONDS));
            cache.invalidate("definition-1");
            continueCopy.countDown();

            assertFalse(stalePut.get(5, TimeUnit.SECONDS));
            assertNull(cache.get("definition-1", 1));
        } finally {
            continueCopy.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static ProcessDefinitionDetailDTO blockingPublishedDefinition(
            String id, Integer version, CountDownLatch copyStarted, CountDownLatch continueCopy) {
        BlockingProcessDefinitionDetailDTO definition =
                new BlockingProcessDefinitionDetailDTO(copyStarted, continueCopy);
        definition.setId(id);
        definition.setProcessCode("expense");
        definition.setProcessName("Expense approval");
        definition.setSystemCode("finance");
        definition.setVersion(version);
        definition.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
        definition.setActivationStatus(ActivationStatusEnum.ACTIVE);
        definition.setNodes(new ArrayList<ProcessNodeDTO>());
        return definition;
    }

    private static ProcessDefinitionDetailDTO publishedDefinition(String id, Integer version) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId(id);
        definition.setProcessCode("expense");
        definition.setProcessName("Expense approval");
        definition.setSystemCode("finance");
        definition.setVersion(version);
        definition.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
        definition.setActivationStatus(ActivationStatusEnum.ACTIVE);

        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setId("node-1");
        node.setDefinitionId(id);
        node.setNodeCode("review");
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(ApproverRuleTypeEnum.USER);
        node.setApproverRuleConfig("{\"userIds\":[\"u1\"]}");
        node.setListenerConfig("{\"onCreate\":true}");
        definition.setNodes(new ArrayList<ProcessNodeDTO>(Arrays.asList(node)));

        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setId("edge-1");
        edge.setDefinitionId(id);
        edge.setEdgeCode("e1");
        edge.setSourceNodeCode("start");
        edge.setTargetNodeCode("review");
        definition.setEdges(new ArrayList<ProcessEdgeDTO>(Arrays.asList(edge)));

        ProcessFormFieldDTO formField = new ProcessFormFieldDTO();
        formField.setId("field-1");
        formField.setDefinitionId(id);
        formField.setFieldCode("amount");
        formField.setValidationRule("{\"min\":1}");
        definition.setFormFields(new ArrayList<ProcessFormFieldDTO>(Arrays.asList(formField)));

        ProcessAttachmentTemplateDTO attachment = new ProcessAttachmentTemplateDTO();
        attachment.setId("attachment-1");
        attachment.setDefinitionId(id);
        attachment.setAttachmentConfigId("config-1");
        attachment.setAttachmentTemplateId("template-1");
        attachment.setAttachmentCode("receipt");
        attachment.setAllowedExtensions(new ArrayList<String>(Arrays.asList("pdf", "png")));
        attachment.setApplicableNodeCodes(new ArrayList<String>(Collections.singletonList("review")));
        definition.setAttachmentTemplates(
                new ArrayList<ProcessAttachmentTemplateDTO>(Arrays.asList(attachment)));
        return definition;
    }

    private static ValidationResult validResult() {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        return result;
    }

    private static ValidationResult invalidResult() {
        ValidationResult result = new ValidationResult();
        result.setValid(false);
        return result;
    }

    /** 在缓存复制节点列表时暂停，用于编排在途加载和失效交错。 */
    private static final class BlockingProcessDefinitionDetailDTO extends ProcessDefinitionDetailDTO {
        /** 节点列表开始复制信号。 */
        private final CountDownLatch copyStarted;
        /** 继续复制信号。 */
        private final CountDownLatch continueCopy;

        private BlockingProcessDefinitionDetailDTO(CountDownLatch copyStarted,
                                                    CountDownLatch continueCopy) {
            this.copyStarted = copyStarted;
            this.continueCopy = continueCopy;
        }

        @Override
        public List<ProcessNodeDTO> getNodes() {
            copyStarted.countDown();
            try {
                if (!continueCopy.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to continue cache copy");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to continue cache copy",
                        exception);
            }
            return super.getNodes();
        }
    }
}
