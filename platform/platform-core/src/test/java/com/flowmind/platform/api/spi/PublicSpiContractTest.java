package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.StoreFileRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicSpiContractTest {

    @Test
    void fileStorageProviderUsesOnlyPublicApiContracts() throws NoSuchMethodException {
        Method store = FileStorageProvider.class.getMethod("store", StoreFileRequest.class);
        Method load = FileStorageProvider.class.getMethod("load", String.class);

        assertEquals(StoredFile.class, store.getReturnType());
        assertEquals(FileContent.class, load.getReturnType());
        assertPublicApiTypes(store);
        assertPublicApiTypes(load);
    }

    @Test
    void messagePublisherUsesPublicMessageContract() throws NoSuchMethodException {
        Method publish = MessagePublisher.class.getMethod("publish", ProcessMessage.class);

        assertEquals(Void.TYPE, publish.getReturnType());
        assertPublicApiTypes(publish);
    }

    private void assertPublicApiTypes(Method method) {
        assertFalse(isPersistenceType(method.getReturnType()), method + " exposes a persistence return type");
        for (Class<?> parameterType : method.getParameterTypes()) {
            assertFalse(isPersistenceType(parameterType), method + " exposes a persistence parameter type");
        }
    }

    private boolean isPersistenceType(Class<?> type) {
        Package typePackage = type.getPackage();
        return typePackage != null && typePackage.getName().startsWith("com.flowmind.platform.persistence");
    }
}
