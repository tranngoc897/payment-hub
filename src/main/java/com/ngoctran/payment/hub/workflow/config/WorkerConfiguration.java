package com.ngoctran.payment.hub.workflow.config;

import com.ngoctran.payment.hub.scheduler.AdvancedPipelineWorkflowImpl;
import com.ngoctran.payment.hub.scheduler.activity.AccountVerificationActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.ComplianceCheckActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.FraudDetectionActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.IdempotencyCheckActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.PaymentCompensationActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.PaymentConfirmationActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.PaymentExecutionActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.PaymentRoutingActivityImpl;
import com.ngoctran.payment.hub.scheduler.activity.PaymentValidationActivityImpl;
import com.ngoctran.payment.hub.reconciliation.ReconciliationWorkflowImpl;
import com.ngoctran.payment.hub.scheduler.PaymentWorkflowImpl;
import com.ngoctran.payment.hub.scheduler.PaymentMonitorWorkflowImpl;
import com.ngoctran.payment.hub.reconciliation.activity.DataCollectionActivityImpl;
import com.ngoctran.payment.hub.reconciliation.activity.DiscrepancyAnalysisActivityImpl;
import com.ngoctran.payment.hub.reconciliation.activity.EscalationActivityImpl;
import com.ngoctran.payment.hub.reconciliation.activity.MatchingActivityImpl;
import com.ngoctran.payment.hub.reconciliation.activity.ReportingActivityImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class WorkerConfiguration {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkerConfiguration.class);
    private final WorkerFactory workerFactory;
    // Payment activity implementations
    private final PaymentValidationActivityImpl paymentValidationActivity;
    private final AccountVerificationActivityImpl accountVerificationActivity;
    private final ComplianceCheckActivityImpl complianceCheckActivity;
    private final FraudDetectionActivityImpl fraudDetectionActivity;
    private final PaymentRoutingActivityImpl paymentRoutingActivity;
    private final PaymentExecutionActivityImpl paymentExecutionActivity;
    private final PaymentConfirmationActivityImpl paymentConfirmationActivity;
    private final PaymentCompensationActivityImpl paymentCompensationActivity;
    private final IdempotencyCheckActivityImpl idempotencyCheckActivity;
    // Reconciliation activity implementations
    private final DataCollectionActivityImpl dataCollectionActivity;
    private final MatchingActivityImpl matchingActivity;
    private final DiscrepancyAnalysisActivityImpl discrepancyAnalysisActivity;
    private final EscalationActivityImpl escalationActivity;
    private final ReportingActivityImpl reportingActivity;

    /**
     * Task Queue Names
     */
    public static final String GENERAL_QUEUE = "GENERAL_QUEUE";
    public static final String RECONCILIATION_QUEUE = "RECONCILIATION_QUEUE";

    @PostConstruct
    public void registerWorkersAndActivities() {
        log.info("Registering Temporal Workers and Activities...");
        // Register General Worker
        registerGeneralWorker();
        // Register Reconciliation Worker
        registerReconciliationWorker();
        // Start all workers
        workerFactory.start();

        log.info("All Temporal Workers started successfully");
    }


    private void registerGeneralWorker() {
        log.info("Registering General Worker on queue: {}", GENERAL_QUEUE);

        Worker worker = workerFactory.newWorker(GENERAL_QUEUE);
        // Register Scheduled Workflow and Dynamic Pipelines
        worker.registerWorkflowImplementationTypes(
                AdvancedPipelineWorkflowImpl.class,
                PaymentWorkflowImpl.class,
                PaymentMonitorWorkflowImpl.class);

        // Register activity implementations
        worker.registerActivitiesImplementations(
                paymentValidationActivity,
                accountVerificationActivity,
                complianceCheckActivity,
                fraudDetectionActivity,
                paymentRoutingActivity,
                paymentExecutionActivity,
                paymentConfirmationActivity,
                paymentCompensationActivity,
                idempotencyCheckActivity);

        log.info("General Worker registered successfully");
    }

    private void registerReconciliationWorker() {
        log.info("Registering Reconciliation Worker on queue: {}", RECONCILIATION_QUEUE);
        Worker worker = workerFactory.newWorker(RECONCILIATION_QUEUE);
        // Register reconciliation workflow implementation
        worker.registerWorkflowImplementationTypes(ReconciliationWorkflowImpl.class);
        // Register reconciliation activity implementations
        worker.registerActivitiesImplementations(
                dataCollectionActivity,
                matchingActivity,
                discrepancyAnalysisActivity,
                escalationActivity,
                reportingActivity);

        log.info("Reconciliation Worker registered successfully");
    }
}
