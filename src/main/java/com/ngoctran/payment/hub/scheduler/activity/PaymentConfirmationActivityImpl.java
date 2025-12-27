package com.ngoctran.payment.hub.scheduler.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Payment Confirmation Activity Implementation
 * Confirms and finalizes scheduler processing
 */
@Component
@Slf4j
public class PaymentConfirmationActivityImpl implements PaymentConfirmationActivity {

    @Override
    public PaymentConfirmationResult confirmPayment(String paymentId, String transactionId,
                                                  PaymentExecutionActivity.PaymentExecutionResult executionResult) {
        log.info("Confirming scheduler: id={}, transactionId={}", paymentId, transactionId);

        try {
            // Check if execution was successful
            if (!executionResult.isSuccess()) {
                log.warn("Cannot confirm failed scheduler: {}", paymentId);
                return new PaymentConfirmationResult(false, null, "FAILED",
                        "Payment execution failed, cannot confirm", null);
            }

            // Generate confirmation ID
            String confirmationId = "CONF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // Simulate confirmation steps
            String notificationSent = sendConfirmationNotification(paymentId, transactionId);
            String receiptGenerated = generatePaymentReceipt(paymentId, transactionId, executionResult);
            String auditLogged = logPaymentAudit(paymentId, transactionId, executionResult);

            ConfirmationDetails details = new ConfirmationDetails(
                    notificationSent,
                    receiptGenerated,
                    auditLogged,
                    System.currentTimeMillis()
            );

            log.info("Payment confirmed successfully: confirmationId={}, paymentId={}", confirmationId, paymentId);

            return new PaymentConfirmationResult(true, confirmationId, "CONFIRMED",
                    "Payment confirmed successfully", details);

        } catch (Exception e) {
            log.error("Payment confirmation failed for scheduler: {}", paymentId, e);
            return new PaymentConfirmationResult(false, null, "ERROR",
                    "Confirmation failed: " + e.getMessage(), null);
        }
    }

    /**
     * Send confirmation notification
     */
    private String sendConfirmationNotification(String paymentId, String transactionId) {
        // Simulate sending notification (email, SMS, push notification)
        log.debug("Sending confirmation notification for scheduler: {}", paymentId);
        return "NOTIFICATION_SENT";
    }

    /**
     * Generate scheduler receipt
     */
    private String generatePaymentReceipt(String paymentId, String transactionId,
                                        PaymentExecutionActivity.PaymentExecutionResult executionResult) {
        // Simulate generating PDF receipt or receipt record
        log.debug("Generating scheduler receipt for scheduler: {}", paymentId);
        return "RECEIPT_GENERATED";
    }

    /**
     * Log scheduler audit trail
     */
    private String logPaymentAudit(String paymentId, String transactionId,
                                 PaymentExecutionActivity.PaymentExecutionResult executionResult) {
        // Simulate logging to audit system
        log.debug("Logging scheduler audit for scheduler: {}", paymentId);
        return "AUDIT_LOGGED";
    }
}
