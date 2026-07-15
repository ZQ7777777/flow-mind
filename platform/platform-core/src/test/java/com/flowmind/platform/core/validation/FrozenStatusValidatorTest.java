package com.flowmind.platform.core.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrozenStatusValidatorTest {

    private final DefinitionStatusValidator definitionStatusValidator = new DefinitionStatusValidator();
    private final ProcessInstanceStatusValidator instanceStatusValidator = new ProcessInstanceStatusValidator();
    private final ActiveTaskStatusValidator taskStatusValidator = new ActiveTaskStatusValidator();
    private final TaskGroupStatusValidator taskGroupStatusValidator = new TaskGroupStatusValidator();
    private final OperationRecordStatusValidator operationStatusValidator = new OperationRecordStatusValidator();

    @ParameterizedTest(name = "{0}/{1}/{2}")
    @MethodSource("validDefinitionCombinations")
    void validDefinitionStatusCombinationsPass(String definitionStatus,
                                               String activationStatus,
                                               String grayStatus,
                                               String grayRuleConfig) {
        assertDoesNotThrow(() -> definitionStatusValidator.validate(
                definitionStatus, activationStatus, grayStatus, grayRuleConfig));
    }

    @ParameterizedTest(name = "{0}/{1}/{2}")
    @MethodSource("invalidDefinitionCombinations")
    void invalidDefinitionStatusCombinationsUseFrozenErrorCode(String definitionStatus,
                                                              String activationStatus,
                                                              String grayStatus,
                                                              String grayRuleConfig,
                                                              String expectedCode) {
        FrozenValidationException exception = assertThrows(FrozenValidationException.class,
                () -> definitionStatusValidator.validate(
                        definitionStatus, activationStatus, grayStatus, grayRuleConfig));

        assertEquals(expectedCode, exception.getErrorCode());
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("validInstanceTransitions")
    void validInstanceStatusTransitionsPass(String sourceStatus, String targetStatus) {
        assertDoesNotThrow(() -> instanceStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("invalidInstanceTransitions")
    void invalidInstanceStatusTransitionsUseFrozenErrorCode(String sourceStatus, String targetStatus) {
        assertFrozenCode(FrozenValidationErrorCodes.INSTANCE_STATUS_TRANSITION_INVALID,
                () -> instanceStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("validTaskTransitions")
    void validTaskStatusTransitionsPass(String sourceStatus, String targetStatus) {
        assertDoesNotThrow(() -> taskStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("invalidTaskTransitions")
    void invalidTaskStatusTransitionsUseFrozenErrorCode(String sourceStatus, String targetStatus) {
        assertFrozenCode(FrozenValidationErrorCodes.TASK_STATUS_TRANSITION_INVALID,
                () -> taskStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("validTaskGroupTransitions")
    void validTaskGroupStatusTransitionsPass(String sourceStatus, String targetStatus) {
        assertDoesNotThrow(() -> taskGroupStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("invalidTaskGroupTransitions")
    void invalidTaskGroupStatusTransitionsUseFrozenErrorCode(String sourceStatus, String targetStatus) {
        assertFrozenCode(FrozenValidationErrorCodes.TASK_GROUP_STATUS_TRANSITION_INVALID,
                () -> taskGroupStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("validOperationTransitions")
    void validOperationRecordStatusTransitionsPass(String sourceStatus, String targetStatus) {
        assertDoesNotThrow(() -> operationStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}->{1}")
    @MethodSource("invalidOperationTransitions")
    void invalidOperationRecordStatusTransitionsUseFrozenErrorCode(String sourceStatus, String targetStatus) {
        assertFrozenCode(FrozenValidationErrorCodes.OPERATION_STATUS_TRANSITION_INVALID,
                () -> operationStatusValidator.validateTransition(sourceStatus, targetStatus));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validTaskGroupCountFixtures")
    void validTaskGroupCountsPass(TaskGroupCountFixture fixture) {
        assertDoesNotThrow(() -> taskGroupStatusValidator.validateCounts(
                fixture.groupType, fixture.groupStatus, fixture.totalCount,
                fixture.completedCount, fixture.branchStates));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTaskGroupCountFixtures")
    void invalidTaskGroupCountsUseFrozenErrorCode(TaskGroupCountFixture fixture) {
        assertFrozenCode(FrozenValidationErrorCodes.TASK_GROUP_COUNT_INVALID,
                () -> taskGroupStatusValidator.validateCounts(
                        fixture.groupType, fixture.groupStatus, fixture.totalCount,
                        fixture.completedCount, fixture.branchStates));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validBranchArrivalFixtures")
    void validBranchArrivalsPass(BranchArrivalFixture fixture) {
        assertDoesNotThrow(() -> taskGroupStatusValidator.validateBranchArrival(
                fixture.groupStatus, fixture.totalCount, fixture.completedCount,
                fixture.branchStates, fixture.branchKey));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBranchArrivalFixtures")
    void invalidBranchArrivalsUseFrozenErrorCode(BranchArrivalFixture fixture) {
        assertFrozenCode(FrozenValidationErrorCodes.TASK_GROUP_BRANCH_ARRIVAL_INVALID,
                () -> taskGroupStatusValidator.validateBranchArrival(
                        fixture.groupStatus, fixture.totalCount, fixture.completedCount,
                        fixture.branchStates, fixture.branchKey));
    }

    private static Stream<Arguments> validDefinitionCombinations() {
        return Stream.of(
                Arguments.of("DRAFT", "INACTIVE", "OFF", null),
                Arguments.of("PUBLISHED", "INACTIVE", "OFF", null),
                Arguments.of("PUBLISHED", "ACTIVE", "OFF", null),
                Arguments.of("PUBLISHED", "ACTIVE", "ON", "{\"userIds\":[\"u1\"]}"),
                Arguments.of("ARCHIVED", "INACTIVE", "OFF", null));
    }

    private static Stream<Arguments> invalidDefinitionCombinations() {
        return Stream.of(
                Arguments.of("DRAFT", "ACTIVE", "OFF", null,
                        FrozenValidationErrorCodes.DEFINITION_STATUS_COMBINATION_INVALID),
                Arguments.of("PUBLISHED", "ACTIVE", "ON", null,
                        FrozenValidationErrorCodes.DEFINITION_GRAY_RULE_REQUIRED),
                Arguments.of("ARCHIVED", "ACTIVE", "OFF", null,
                        FrozenValidationErrorCodes.DEFINITION_STATUS_COMBINATION_INVALID),
                Arguments.of("UNKNOWN", "INACTIVE", "OFF", null,
                        FrozenValidationErrorCodes.DEFINITION_STATUS_COMBINATION_INVALID));
    }

    private static Stream<Arguments> validInstanceTransitions() {
        return Stream.of(
                Arguments.of("NOT_STARTED", "RUNNING"),
                Arguments.of("RUNNING", "COMPLETED"),
                Arguments.of("RUNNING", "TERMINATED"),
                Arguments.of("COMPLETED", "ARCHIVED"),
                Arguments.of("TERMINATED", "ARCHIVED"));
    }

    private static Stream<Arguments> invalidInstanceTransitions() {
        return Stream.of(
                Arguments.of("RUNNING", "NOT_STARTED"),
                Arguments.of("COMPLETED", "RUNNING"),
                Arguments.of("TERMINATED", "RUNNING"),
                Arguments.of("ARCHIVED", "COMPLETED"),
                Arguments.of("ARCHIVED", "TERMINATED"),
                Arguments.of("NOT_STARTED", "COMPLETED"));
    }

    private static Stream<Arguments> validTaskTransitions() {
        return Stream.of(
                Arguments.of("ACTIVE", "CLAIMED"),
                Arguments.of("CLAIMED", "ACTIVE"),
                Arguments.of("ACTIVE", "COMPLETED"),
                Arguments.of("CLAIMED", "COMPLETED"),
                Arguments.of("ACTIVE", "CANCELED"),
                Arguments.of("CLAIMED", "CANCELED"));
    }

    private static Stream<Arguments> invalidTaskTransitions() {
        return Stream.of(
                Arguments.of("CLAIMED", "CLAIMED"),
                Arguments.of("COMPLETED", "ACTIVE"),
                Arguments.of("COMPLETED", "CLAIMED"),
                Arguments.of("CANCELED", "ACTIVE"),
                Arguments.of("CANCELED", "CLAIMED"),
                Arguments.of("COMPLETED", "CANCELED"),
                Arguments.of("ACTIVE", "ACTIVE"));
    }

    private static Stream<Arguments> validTaskGroupTransitions() {
        return Stream.of(
                Arguments.of("ACTIVE", "COMPLETED"),
                Arguments.of("ACTIVE", "CANCELED"));
    }

    private static Stream<Arguments> invalidTaskGroupTransitions() {
        return Stream.of(
                Arguments.of("COMPLETED", "ACTIVE"),
                Arguments.of("CANCELED", "ACTIVE"),
                Arguments.of("ACTIVE", "ACTIVE"));
    }

    private static Stream<Arguments> validOperationTransitions() {
        return Stream.of(
                Arguments.of("PROCESSING", "SUCCESS"),
                Arguments.of("PROCESSING", "FAILED"));
    }

    private static Stream<Arguments> invalidOperationTransitions() {
        return Stream.of(
                Arguments.of("SUCCESS", "PROCESSING"),
                Arguments.of("FAILED", "PROCESSING"),
                Arguments.of("PROCESSING", "PROCESSING"));
    }

    private static Stream<TaskGroupCountFixture> validTaskGroupCountFixtures() {
        return Stream.of(
                countFixture("active countersign", "COUNTERSIGN", "ACTIVE", 2, 1, null),
                countFixture("completed or-sign", "OR_SIGN", "COMPLETED", 3, 1, null),
                countFixture("active parallel", "PARALLEL_GATEWAY", "ACTIVE", 2, 1,
                        branchStates("branch-a", "ARRIVED", "branch-b", "RUNNING")));
    }

    private static Stream<TaskGroupCountFixture> invalidTaskGroupCountFixtures() {
        return Stream.of(
                countFixture("completed exceeds total", "COUNTERSIGN", "ACTIVE", 2, 3, null),
                countFixture("active has all completed", "COUNTERSIGN", "ACTIVE", 2, 2, null),
                countFixture("parallel branch count mismatch", "PARALLEL_GATEWAY", "ACTIVE", 2, 1,
                        branchStates("branch-a", "ARRIVED")),
                countFixture("parallel arrived count mismatch", "PARALLEL_GATEWAY", "ACTIVE", 2, 0,
                        branchStates("branch-a", "ARRIVED", "branch-b", "RUNNING")));
    }

    private static Stream<BranchArrivalFixture> validBranchArrivalFixtures() {
        return Stream.of(branchFixture("running branch", "ACTIVE", 2, 0,
                branchStates("branch-a", "RUNNING", "branch-b", "RUNNING"), "branch-a"));
    }

    private static Stream<BranchArrivalFixture> invalidBranchArrivalFixtures() {
        return Stream.of(
                branchFixture("already arrived branch", "ACTIVE", 2, 1,
                        branchStates("branch-a", "ARRIVED", "branch-b", "RUNNING"), "branch-a"),
                branchFixture("unknown branch", "ACTIVE", 2, 0,
                        branchStates("branch-a", "RUNNING", "branch-b", "RUNNING"), "branch-c"),
                branchFixture("terminal group", "COMPLETED", 2, 2,
                        branchStates("branch-a", "ARRIVED", "branch-b", "ARRIVED"), "branch-a"));
    }

    private void assertFrozenCode(String expectedCode, Runnable action) {
        FrozenValidationException exception = assertThrows(FrozenValidationException.class, action::run);
        assertEquals(expectedCode, exception.getErrorCode());
    }

    private static Map<String, String> branchStates(String key1, String value1) {
        Map<String, String> states = new LinkedHashMap<String, String>();
        states.put(key1, value1);
        return states;
    }

    private static Map<String, String> branchStates(String key1, String value1,
                                                    String key2, String value2) {
        Map<String, String> states = branchStates(key1, value1);
        states.put(key2, value2);
        return states;
    }

    private static TaskGroupCountFixture countFixture(String name,
                                                      String groupType,
                                                      String groupStatus,
                                                      int totalCount,
                                                      int completedCount,
                                                      Map<String, String> branchStates) {
        return new TaskGroupCountFixture(
                name, groupType, groupStatus, totalCount, completedCount, branchStates);
    }

    private static BranchArrivalFixture branchFixture(String name,
                                                     String groupStatus,
                                                     int totalCount,
                                                     int completedCount,
                                                     Map<String, String> branchStates,
                                                     String branchKey) {
        return new BranchArrivalFixture(
                name, groupStatus, totalCount, completedCount, branchStates, branchKey);
    }

    private static final class TaskGroupCountFixture {
        private final String name;
        private final String groupType;
        private final String groupStatus;
        private final int totalCount;
        private final int completedCount;
        private final Map<String, String> branchStates;

        private TaskGroupCountFixture(String name,
                                      String groupType,
                                      String groupStatus,
                                      int totalCount,
                                      int completedCount,
                                      Map<String, String> branchStates) {
            this.name = name;
            this.groupType = groupType;
            this.groupStatus = groupStatus;
            this.totalCount = totalCount;
            this.completedCount = completedCount;
            this.branchStates = branchStates;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class BranchArrivalFixture {
        private final String name;
        private final String groupStatus;
        private final int totalCount;
        private final int completedCount;
        private final Map<String, String> branchStates;
        private final String branchKey;

        private BranchArrivalFixture(String name,
                                     String groupStatus,
                                     int totalCount,
                                     int completedCount,
                                     Map<String, String> branchStates,
                                     String branchKey) {
            this.name = name;
            this.groupStatus = groupStatus;
            this.totalCount = totalCount;
            this.completedCount = completedCount;
            this.branchStates = branchStates;
            this.branchKey = branchKey;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
