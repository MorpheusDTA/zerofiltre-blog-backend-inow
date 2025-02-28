package tech.zerofiltre.blog.infra.providers.api.stripe;

import com.stripe.model.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;
import org.thymeleaf.*;
import org.thymeleaf.context.*;
import tech.zerofiltre.blog.domain.course.*;
import tech.zerofiltre.blog.domain.course.use_cases.enrollment.*;
import tech.zerofiltre.blog.domain.error.*;
import tech.zerofiltre.blog.domain.user.*;
import tech.zerofiltre.blog.domain.user.model.*;
import tech.zerofiltre.blog.domain.user.use_cases.*;
import tech.zerofiltre.blog.infra.*;
import tech.zerofiltre.blog.infra.providers.notification.user.*;
import tech.zerofiltre.blog.infra.providers.notification.user.model.*;

import java.util.*;

import static tech.zerofiltre.blog.domain.user.model.User.Plan.*;

@Slf4j
@Component
public class StripeCommons {

    public static final String USER_ID = "userId";
    public static final String PRODUCT_ID = "productId";
    public static final String VOTRE_PAIEMENT_CHEZ_ZEROFILTRE = "Votre paiement chez Zerofiltre";
    public static final String SIGNATURE = "\n\n L'équipe Zerofiltre";
    public static final String SUBSCRIPTION_CREATE_BILLING_REASON = "subscription_create";
    public static final String TOTAL_PAID_COUNT = "totalPaidCount";
    public static final String EVENT_ID_EVENT_TYPE_PRICE = "EventId= {}, EventType={}, Price: {}";

    private final UserProvider userProvider;
    private final Enroll enroll;
    private final Suspend suspend;
    private final ZerofiltreEmailSender emailSender;
    private final InfraProperties infraProperties;
    private final ITemplateEngine emailTemplateEngine;


    public StripeCommons(UserProvider userProvider, EnrollmentProvider enrollmentProvider, CourseProvider courseProvider, ChapterProvider chapterProvider, ZerofiltreEmailSender emailSender, InfraProperties infraProperties, ITemplateEngine emailTemplateEngine) {
        this.userProvider = userProvider;
        this.emailSender = emailSender;
        this.infraProperties = infraProperties;
        this.emailTemplateEngine = emailTemplateEngine;
        enroll = new Enroll(enrollmentProvider, courseProvider, userProvider, chapterProvider);
        suspend = new Suspend(enrollmentProvider, courseProvider, chapterProvider);
    }

    public void fulfillOrder(String userId, com.stripe.model.Product productObject, boolean paymentSuccess, Event event, Customer customer) throws ZerofiltreException {
        if (productObject == null) return;
        log.info("EventId= {}, EventType={}, Product object: {}", event.getId(), event.getType(), productObject.toString().replace("\n", " "));

        log.info("EventId= {}, EventType={},User id: {}", event.getId(), event.getType(), userId);

        if (infraProperties.getProPlanProductId().equals(productObject.getId())) { //subscription to PRO
            log.info("EventId= {}, EventType={}, Handling User {} pro plan subscription", event.getId(), event.getType(), userId);
            updateUserInfo(userId, paymentSuccess, event, customer, true);
            log.info("EventId= {}, EventType={}, Handled User {} pro plan subscription", event.getId(), event.getType(), userId);
        } else {
            long productId = Long.parseLong(productObject.getMetadata().get(PRODUCT_ID));
            log.info("EventId= {}, EventType={}, Product id: {}", event.getId(), event.getType(), productId);
            if (paymentSuccess) {
                enroll.execute(Long.parseLong(userId), productId, false);
                log.info("EventId= {}, EventType={}, User of id={} enrolled in Product id: {}", event.getId(), event.getType(), userId, productId);
            } else {
                suspend.execute(Long.parseLong(userId), productId);
                log.info("EventId= {}, EventType={}, User of id={} suspended from Product id: {}", event.getId(), event.getType(), userId, productId);
            }
            updateUserInfo(userId, paymentSuccess, event, customer, false);
        }
    }

    private void updateUserInfo(String userId, boolean paymentSuccess, Event event, Customer customer, boolean isPro) throws ZerofiltreException {
        User user = userProvider.userOfId(Long.parseLong(userId))
                .orElseThrow(() -> {
                    log.error("EventId= {}, EventType={}, We couldn't find the user {} to edit", event.getId(), event.getType(), userId);
                    return new UserNotFoundException("EventId= " + event.getId() + ",EventType= " + event.getType() + " We couldn't find the user " + userId + " to edit", userId);
                });
        if (isPro && !paymentSuccess) {
            user.setPlan(BASIC);
            suspend.all(Long.parseLong(userId), PRO);
        } else if (isPro) {
            user.setPlan(PRO);
        }
        String paymentEmail = customer.getEmail();
        user.setPaymentEmail(paymentEmail);
        userProvider.save(user);
    }

    public void notifyUser(Customer customer, String subject, String message) {
        try {
            Email email = new Email();
            email.setRecipients(Collections.singletonList(customer.getEmail()));
            email.setSubject(subject);
            email.setReplyTo("info@zerofiltre.tech");

            Map<String, Object> templateModel = new HashMap<>();
            templateModel.put("content", message);
            Context thymeleafContext = new Context();
            thymeleafContext.setVariables(templateModel);
            thymeleafContext.setLocale(Locale.FRENCH);

            String emailContent = emailTemplateEngine.process("general_message.html", thymeleafContext);
            email.setContent(emailContent);

            emailSender.send(email);
        } catch (Exception e) {
            log.warn("Failed to notify user {} about payment with this subject {} with message {}", customer != null ? customer.getEmail() : "unknown user", subject, message);
        }
    }


}
