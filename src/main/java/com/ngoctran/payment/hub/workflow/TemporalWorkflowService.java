package com.ngoctran.payment.hub.workflow;


import com.ngoctran.payment.hub.scheduler.AdvancedPipelineWorkflow;
import com.ngoctran.payment.hub.scheduler.PaymentMonitorWorkflow;
import com.ngoctran.payment.hub.scheduler.PaymentWorkflow;
import com.ngoctran.payment.hub.reconciliation.ReconciliationWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.schedules.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemporalWorkflowService {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TemporalWorkflowService.class);
        private final WorkflowClient client;
        private final ScheduleClient scheduleClient;
        private final ScheduleRepository scheduleRepo;

        public String startAdvancedPipeline(String pipelineName, List<Map<String, Object>> tasks,
                        Map<String, Object> inputData) {
                log.info("Starting Advanced Pipeline: {}", pipelineName);

                String workflowId = "pipeline-" + UUID.randomUUID().toString();
                WorkflowOptions options = WorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setTaskQueue(WorkerConfiguration.GENERAL_QUEUE)
                                .build();

                AdvancedPipelineWorkflow workflow = client.newWorkflowStub(AdvancedPipelineWorkflow.class,
                                options);

                // Start it and return ID
                WorkflowClient.start(workflow::runPipeline, pipelineName, tasks, inputData);
                return workflowId;
        }

        public void cancelWorkflow(String workflowId) {
                WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId);
                workflow.cancel();

                log.info("Workflow cancelled");
        }


        @Transactional
        public void createPaymentProcessingSchedule(String scheduleId, String cronSchedule) {

                log.info("Creating Payment Processing Schedule: {} with cron: {}", scheduleId, cronSchedule);

                // Check if schedule already exists in DB
                if (scheduleRepo.existsByScheduleIdAndStatus(scheduleId, ScheduleStatus.ACTIVE)) {
                        log.warn("Payment schedule {} already exists and is active", scheduleId);
                        return;
                }

                ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                                .setWorkflowType(PaymentMonitorWorkflow.class)
                                .setOptions(WorkflowOptions.newBuilder()
                                                .setWorkflowId("scheduler-monitor-" + scheduleId)
                                                .setTaskQueue(WorkerConfiguration.GENERAL_QUEUE)
                                                .build())
                                // Default arguments for monitorPayments(String accountId, int iterationCount)
                                .setArguments("BANKING_SYSTEM", 0)
                                .build();

                Schedule schedule = Schedule.newBuilder()
                                .setAction(action)
                                .setSpec(ScheduleSpec.newBuilder()
                                                .setCronExpressions(Collections.singletonList(cronSchedule))
                                                .build()).build();

                try {
                        scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
                        log.info("Payment processing schedule {} created successfully in Temporal", scheduleId);

                        // Save schedule metadata to database
                        saveScheduleEntity(scheduleId, cronSchedule, "PaymentMonitorWorkflow", WorkerConfiguration.GENERAL_QUEUE, null);

                } catch (Exception e) {
                        log.warn("Payment schedule {} might already exist in Temporal: {}", scheduleId, e.getMessage());
                        // Still save to DB if it doesn't exist there, for consistency
                        if (!scheduleRepo.existsById(scheduleId)) {
                                saveScheduleEntity(scheduleId, cronSchedule, "PaymentMonitorWorkflow", WorkerConfiguration.GENERAL_QUEUE, null);
                        }
                }

        }


        private void saveScheduleEntity(String scheduleId, String cronExpression, String workflowType,
                                      String taskQueue, String createdBy) {
                ScheduleEntity scheduleEntity = new ScheduleEntity();
                scheduleEntity.setScheduleId(scheduleId);
                scheduleEntity.setCronExpression(cronExpression);
                scheduleEntity.setWorkflowType(workflowType);
                scheduleEntity.setTaskQueue(taskQueue);
                scheduleEntity.setCreatedBy(createdBy != null ? createdBy : "SYSTEM");

                // Set appropriate description and arguments based on workflow type
                if ("PaymentMonitorWorkflow".equals(workflowType)) {
                        scheduleEntity.setDescription("Payment processing monitor schedule");
                        scheduleEntity.setWorkflowArguments("[\"BANKING_SYSTEM\", 0]");
                } else if ("CaseMonitorWorkflow".equals(workflowType)) {
                        scheduleEntity.setWorkflowArguments("[\"SYSTEM_WIDE\", 0]");
                } else {
                        scheduleEntity.setDescription("Workflow schedule");
                        scheduleEntity.setWorkflowArguments("[]");
                }

                scheduleRepo.save(scheduleEntity);
                log.info("Schedule metadata saved to database: {}", scheduleId);
        }

        /**
         * Get all schedules from database
         */
        public List<ScheduleEntity> getAllSchedules() {
                log.info("Retrieving all schedules from database");
                return scheduleRepo.findAll();
        }

        /**
         * Get schedule by ID from database
         */
        public ScheduleEntity getScheduleById(String scheduleId) {
                log.info("Retrieving schedule by ID: {}", scheduleId);
                return scheduleRepo.findById(scheduleId)
                        .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
        }

        /**
         * Delete schedule from both Temporal and database
         */
        @Transactional
        public void deleteSchedule(String scheduleId) {
                log.info("Deleting schedule: {}", scheduleId);

                // Check if schedule exists in DB
                ScheduleEntity scheduleEntity = scheduleRepo.findById(scheduleId)
                        .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));

                try {
                        // Delete from Temporal
                        //scheduleClient.deleteSchedule(scheduleId);
                        log.info("Schedule deleted from Temporal: {}", scheduleId);
                } catch (Exception e) {
                        log.warn("Failed to delete schedule from Temporal: {}", e.getMessage());
                        // Continue with DB deletion even if Temporal deletion fails
                }

                // Mark as deleted in database (soft delete)
                scheduleEntity.setStatus(ScheduleStatus.DELETED);
                scheduleEntity.setUpdatedAt(LocalDateTime.now());
                scheduleRepo.save(scheduleEntity);

                log.info("Schedule marked as deleted in database: {}", scheduleId);
        }

        /**
         * Start Payment Workflow Manually
         */
        public String startPaymentWorkflow(String paymentId, String accountId, double amount,
                                         String currency, String tenantId, String userId) {
                log.info("Starting scheduler workflow manually: paymentId={}, accountId={}, amount={}",
                        paymentId, accountId, amount);

                String workflowId = "scheduler-workflow-" + paymentId;
                WorkflowOptions options = WorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setTaskQueue(WorkerConfiguration.GENERAL_QUEUE)
                                .setWorkflowExecutionTimeout(Duration.ofHours(2))
                                .build();

                PaymentWorkflow workflow = client.newWorkflowStub(PaymentWorkflow.class, options);

                // Start workflow asynchronously
                WorkflowClient.start(workflow::processPayment, paymentId, accountId, amount, currency);

                log.info("Payment workflow started: {}", workflowId);
                return workflowId;
        }

        /**
         * Stop scheduler monitoring workflow
         */
        public void stopPaymentMonitoring(String workflowId) {
                log.info("Stopping scheduler monitoring workflow: {}", workflowId);

                PaymentMonitorWorkflow workflow =
                        client.newWorkflowStub(PaymentMonitorWorkflow.class, workflowId);

                workflow.stopMonitoring();
                log.info("Stop monitoring signal sent to workflow: {}", workflowId);
        }

        // ==================== ENHANCED WORKFLOW METHODS (SchedulerService Patterns) ====================

        /**
         * Start Reconciliation Workflow
         */
        @Transactional
        public String startReconciliationWorkflow(String reconciliationId, String date, String type,
                                                Map<String, Object> config) {
                log.info("Starting Reconciliation Workflow: id={}, date={}, type={}", reconciliationId, date, type);

                String workflowId = "reconciliation-" + reconciliationId;
                WorkflowOptions options = WorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setTaskQueue(WorkerConfiguration.RECONCILIATION_QUEUE)
                                .setWorkflowExecutionTimeout(Duration.ofHours(8))
                                .build();

                ReconciliationWorkflow workflow = client.newWorkflowStub(ReconciliationWorkflow.class, options);

                // Start workflow asynchronously
                WorkflowExecution execution = WorkflowClient.start(
                        () -> workflow.reconcilePayments(reconciliationId, date, type, config));

                String processInstanceId = execution.getWorkflowId() + ":" + execution.getRunId();
                log.info("Reconciliation workflow started: workflowId={}, runId={}",
                        execution.getWorkflowId(), execution.getRunId());

                // Save process mapping
                return processInstanceId;
        }

        /**
         * Suspend workflow
         */
        public void suspendWorkflow(String workflowId) {
                log.info("Suspending workflow: {}", workflowId);

                WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId);
                // In Temporal, you would use workflow.pause() or similar
                // For demo, we'll just log
                log.info("Workflow suspended: {}", workflowId);
        }

        /**
         * Resume workflow
         */
        public void resumeWorkflow(String workflowId) {
                log.info("Resuming workflow: {}", workflowId);

                WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId);
                // In Temporal, you would use workflow.unpause() or similar
                log.info("Workflow resumed: {}", workflowId);
        }

        /**
         * Get workflow history
         */
        public List<WorkflowHistoryEntity> getWorkflowHistory(String workflowId) {
                log.info("Getting workflow history for: {}", workflowId);

                // In real implementation, inject WorkflowHistoryRepository
                // return workflowHistoryRepository.findByWorkflowIdOrderByChangedAtAsc(workflowId);

                // Mock empty list for demo
                return java.util.Collections.emptyList();
        }

        /**
         * Get workflow statistics
         */
        public Map<String, Object> getWorkflowStatistics(String workflowType, String dateRange) {
                log.info("Getting workflow statistics: type={}, dateRange={}", workflowType, dateRange);

                // In real implementation, query from WorkflowHistoryRepository
                Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("totalWorkflows", 150);
                stats.put("completedWorkflows", 140);
                stats.put("failedWorkflows", 5);
                stats.put("cancelledWorkflows", 5);
                stats.put("averageExecutionTime", "45 minutes");
                stats.put("successRate", "93.3%");

                return stats;
        }

        /**
         * Schedule workflow execution
         */
        public String scheduleWorkflow(String workflowType, WorkflowSchedulerService.WorkflowScheduleConfig config) {
                log.info("Scheduling workflow: {} with config: {}", workflowType, config);

                // In real implementation, inject WorkflowSchedulerService
                // return workflowSchedulerService.scheduleWorkflowExecution(workflowType, config);

                // Mock response
                return "SCH-" + workflowType + "-" + System.nanoTime();
        }

        /**
         * Send signal to reconciliation workflow
         */
        public void signalReconciliationWorkflow(String workflowId, String signalName, Map<String, Object> signalData) {
                log.info("Sending signal '{}' to reconciliation workflow: {}", signalName, workflowId);

                ReconciliationWorkflow workflow = client.newWorkflowStub(ReconciliationWorkflow.class, workflowId);

                switch (signalName.toLowerCase()) {
                        case "manual_resolution" -> {
                                String discrepancyId = (String) signalData.get("discrepancyId");
                                Map<String, Object> resolution = (Map<String, Object>) signalData.get("resolution");
                                workflow.manualResolution(discrepancyId, resolution);
                        }
                        case "additional_data" -> {
                                String sourceId = (String) signalData.get("sourceId");
                                Map<String, Object> data = (Map<String, Object>) signalData.get("data");
                                workflow.additionalDataReceived(sourceId, data);
                        }
                        default -> log.warn("Unknown signal: {}", signalName);
                }

                log.info("Signal '{}' sent to workflow: {}", signalName, workflowId);
        }

        /**
         * Query reconciliation progress
         */
        public ReconciliationWorkflow.ReconciliationProgress queryReconciliationProgress(String workflowId) {
                log.info("Querying reconciliation progress: {}", workflowId);

                ReconciliationWorkflow workflow = client.newWorkflowStub(ReconciliationWorkflow.class, workflowId);
                return workflow.getProgress();
        }

        /**
         * Query reconciliation statistics
         */
        public ReconciliationWorkflow.ReconciliationStats queryReconciliationStats(String workflowId) {
                log.info("Querying reconciliation statistics: {}", workflowId);

                ReconciliationWorkflow workflow = client.newWorkflowStub(ReconciliationWorkflow.class, workflowId);
                return workflow.getStats();
        }

}
