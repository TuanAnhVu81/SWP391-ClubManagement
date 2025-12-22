package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * EmailService - Service xử lý việc gửi email trong hệ thống
 * 
 * Service này chịu trách nhiệm gửi các loại email tự động:
 * - Email xác thực tài khoản (verification email) khi user đăng ký
 * - Email quên mật khẩu (forgot password email) khi user yêu cầu reset password
 * 
 * Tất cả email đều được format dạng HTML với template đẹp, responsive, và thân thiện với người dùng.
 * 
 * Cấu hình email (SMTP):
 * - Host: smtp.gmail.com (Gmail SMTP server)
 * - Port: 587 (TLS port)
 * - Username: clubhubfpt@gmail.com
 * - Password: App password từ Gmail (không phải mật khẩu tài khoản thông thường)
 * 
 * Lưu ý về CSS trong email template:
 * - Phải escape tất cả ký tự '%' thành '%%' vì String.formatted() sử dụng '%' làm placeholder
 * - Email client hỗ trợ inline CSS tốt hơn external CSS
 * - Sử dụng table layout để đảm bảo compatibility với các email client cũ
 * 
 * @Service - Đánh dấu đây là một Spring Service, được quản lý bởi Spring Container
 * @RequiredArgsConstructor - Lombok tự động tạo constructor với các field final để dependency injection
 * @FieldDefaults - Lombok: tất cả field là PRIVATE và FINAL (immutable dependencies)
 * @Slf4j - Lombok: tự động tạo logger với tên "log" để ghi log
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EmailService {
    /**
     * JavaMailSender là interface của Spring để gửi email
     * Được cấu hình tự động bởi Spring Boot dựa trên properties trong application.yaml
     * (spring.mail.host, spring.mail.port, spring.mail.username, spring.mail.password)
     * 
     * JavaMailSender hỗ trợ:
     * - Gửi email dạng text hoặc HTML
     * - Gửi email với attachment
     * - Gửi email với nhiều recipient (to, cc, bcc)
     * - SMTP authentication (TLS/SSL)
     */
    JavaMailSender javaMailSender;

    /**
     * Gửi email xác thực tài khoản với nút "Xác Thực Email Ngay"
     * 
     * Phương thức này được gọi khi user đăng ký tài khoản mới.
     * Email chứa link xác thực để user click và kích hoạt tài khoản.
     * 
     * @param to - Email address của người nhận (email của user đăng ký)
     * @param fullName - Tên đầy đủ của user (để personalization trong email)
     * @param verificationLink - Link xác thực có dạng: /users/verify?token=abc123...
     *                         Link này sẽ được gửi trong email, user click vào để verify
     * 
     * Lưu ý:
     * - Email được format dạng HTML với template đẹp và responsive
     * - Link có thời hạn (thường là 1 giờ), được quản lý ở UserService
     */
    public void sendVerificationEmail(String to, String fullName, String verificationLink) {
        // Subject của email - hiển thị trong inbox của user
        String subject = "Xác Thực Email Của Bạn - ClubHub";
        
        // Build HTML content từ template với thông tin user và link xác thực
        String htmlContent = buildVerificationEmailTemplate(fullName, verificationLink);
        
        // Gửi email
        sendEmail(to, subject, htmlContent);
    }

    /**
     * Gửi email mật khẩu mới khi user yêu cầu "Quên mật khẩu"
     * 
     * Phương thức này được gọi khi user yêu cầu reset password (forgot password).
     * Email chứa mật khẩu mới được generate tự động.
     * 
     * @param to - Email address của người nhận (email của user cần reset password)
     * @param fullName - Tên đầy đủ của user (để personalization trong email)
     * @param newPassword - Mật khẩu mới được generate tự động (8 ký tự ngẫu nhiên)
     * 
     * Lưu ý bảo mật:
     * - Mật khẩu được gửi qua email (có rủi ro bảo mật, nhưng phổ biến trong thực tế)
     * - User nên đổi mật khẩu ngay sau khi đăng nhập (được nhắc trong email)
     * - Trong production, có thể cải thiện bằng cách dùng reset token + link thay vì gửi password trực tiếp
     */
    public void sendForgotPasswordEmail(String to, String fullName, String newPassword) {
        // Subject của email
        String subject = "Mật Khẩu Mới - ClubHub";
        
        // Build HTML content từ template với thông tin user và mật khẩu mới
        String htmlContent = buildForgotPasswordEmailTemplate(fullName, newPassword);
        
        // Gửi email
        sendEmail(to, subject, htmlContent);
    }

    /**
     * Gửi email HTML cơ bản - Core method để gửi email
     * 
     * Phương thức này là core method được dùng bởi tất cả các phương thức gửi email khác.
     * Nó xử lý việc tạo MimeMessage, set content, recipient, subject, và gửi đi qua SMTP.
     * 
     * @param to - Email address của người nhận
     * @param subject - Tiêu đề email (subject line)
     * @param htmlContent - Nội dung email dạng HTML (có thể chứa CSS inline, images...)
     * 
     * @throws AppException với ErrorCode.EMAIL_SEND_FAILED nếu có lỗi khi gửi email
     *                      (ví dụ: SMTP connection error, authentication failed, invalid email...)
     * 
     * Lưu ý:
     * - Sử dụng UTF-8 encoding để hỗ trợ tiếng Việt
     * - htmlContent được set với flag true = HTML content (không phải plain text)
     * - Mọi exception từ JavaMail API đều được catch và wrap thành AppException với error code
     */
    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            // Tạo MimeMessage object - đại diện cho một email message
            // MimeMessage hỗ trợ HTML content, attachments, multipart messages...
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            
            // MimeMessageHelper là utility class của Spring để dễ dàng set các thuộc tính của email
            // "utf-8" là encoding để hỗ trợ tiếng Việt và các ký tự đặc biệt
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            // Set nội dung email dạng HTML
            // Parameter thứ 2 (true) báo cho helper biết đây là HTML content, không phải plain text
            helper.setText(htmlContent, true);
            
            // Set địa chỉ người nhận (to)
            helper.setTo(to);
            
            // Set tiêu đề email (subject line - hiển thị trong inbox)
            helper.setSubject(subject);
            
            // Set địa chỉ người gửi (from)
            // Đây là email của hệ thống (clubhubfpt@gmail.com)
            helper.setFrom("clubhubfpt@gmail.com");

            // Gửi email qua SMTP server (sử dụng cấu hình trong application.yaml)
            // JavaMailSender sẽ connect đến SMTP server, authenticate, và gửi email
            javaMailSender.send(mimeMessage);
            
            // Ghi log thành công để tracking và monitoring
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            // Catch exception từ JavaMail API (ví dụ: connection error, authentication failed...)
            // Ghi log error với stack trace để debug
            log.error("Error sending email to {}", to, e);
            
            // Throw AppException với error code phù hợp để Controller có thể xử lý
            // ErrorCode.EMAIL_SEND_FAILED sẽ được handle bởi GlobalExceptionHandler
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * Build HTML template cho email xác thực tài khoản
     * 
     * Phương thức này tạo ra HTML content cho email xác thực với:
     * - Design đẹp, hiện đại với gradient colors
     * - Responsive layout (hoạt động tốt trên mobile và desktop)
     * - Nút CTA (Call To Action) nổi bật để user click vào link xác thực
     * - Thông tin rõ ràng về thời hạn link (1 giờ)
     * 
     * Lưu ý quan trọng về CSS:
     * - Tất cả ký tự '%' trong CSS phải được escape thành '%%'
     *   Lý do: String.formatted() sử dụng '%' làm placeholder (ví dụ: %s, %d)
     *   Nếu không escape, sẽ bị lỗi "IllegalFormatException"
     * - Ví dụ: "width: 100%" phải viết thành "width: 100%%"
     *          "0%%, 50%%" phải viết thành "0%%, 50%%"
     * 
     * @param fullName - Tên đầy đủ của user (để personalization: "Xin chào [fullName]")
     * @param verificationLink - Link xác thực để user click vào
     * @return String - HTML content của email
     */
    private String buildVerificationEmailTemplate(String fullName, String verificationLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f4f4;">
                <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                    <tr>
                        <td align="center" style="padding: 40px 0;">
                            <table role="presentation" style="width: 600px; border-collapse: collapse; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                                <tr>
                                    <td style="padding: 40px 40px 20px 40px; text-align: center; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); border-radius: 10px 10px 0 0;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: 600;">🎓 ClubHub</h1>
                                        <p style="margin: 10px 0 0 0; color: #e0e0e0; font-size: 14px;">Hệ thống quản lý câu lạc bộ sinh viên</p>
                                    </td>
                                </tr>
                                
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 20px 0; color: #333333; font-size: 24px; text-align: center;">
                                            Xác Thực Email Của Bạn
                                        </h2>
                                        
                                        <p style="margin: 0 0 15px 0; color: #555555; font-size: 16px; line-height: 1.6;">
                                            Xin chào <strong>%s</strong>,
                                        </p>
                                        
                                        <p style="margin: 0 0 25px 0; color: #555555; font-size: 16px; line-height: 1.6;">
                                            Cảm ơn bạn đã đăng ký tài khoản tại ClubHub! Để hoàn tất quá trình đăng ký, vui lòng xác thực email của bạn bằng cách nhấn vào nút bên dưới.
                                        </p>
                                        
                                        <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                                            <tr>
                                                <td align="center" style="padding: 20px 0;">
                                                    <a href="%s" target="_blank" style="display: inline-block; padding: 16px 40px; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: #ffffff; text-decoration: none; font-size: 16px; font-weight: 600; border-radius: 50px; box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);">
                                                        ✉️ Xác Thực Email Ngay
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                        
                                        <p style="margin: 25px 0 15px 0; color: #888888; font-size: 14px; line-height: 1.6;">
                                            ⏰ <strong>Lưu ý:</strong> Link xác thực này chỉ có hiệu lực trong vòng <strong>1 giờ</strong>.
                                        </p>
                                        
                                        <p style="margin: 0; color: #888888; font-size: 14px; line-height: 1.6;">
                                            Nếu bạn không thực hiện đăng ký này, vui lòng bỏ qua email này.
                                        </p>
                                    </td>
                                </tr>
                                
                                <tr>
                                    <td style="padding: 30px 40px; background-color: #f8f9fa; border-radius: 0 0 10px 10px; text-align: center;">
                                        <p style="margin: 0; color: #888888; font-size: 12px;">
                                            © 2024 ClubHub - FPT University. All rights reserved.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(fullName, verificationLink);
    }

    /**
     * Build HTML template cho email quên mật khẩu
     * 
     * Phương thức này tạo ra HTML content cho email quên mật khẩu với:
     * - Design tương tự email xác thực (consistent branding)
     * - Hiển thị mật khẩu mới một cách nổi bật trong box có border
     * - Cảnh báo bảo mật: nhắc user đổi mật khẩu ngay sau khi đăng nhập
     * 
     * Lưu ý quan trọng về CSS (giống buildVerificationEmailTemplate):
     * - Tất cả ký tự '%' trong CSS phải được escape thành '%%'
     *   Lý do: String.formatted() sử dụng '%' làm placeholder
     * - Ví dụ: "width: 100%" → "width: 100%%"
     * 
     * @param fullName - Tên đầy đủ của user (để personalization)
     * @param newPassword - Mật khẩu mới được generate tự động (8 ký tự)
     * @return String - HTML content của email
     */
    private String buildForgotPasswordEmailTemplate(String fullName, String newPassword) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f4f4;">
                <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                    <tr>
                        <td align="center" style="padding: 40px 0;">
                            <table role="presentation" style="width: 600px; border-collapse: collapse; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                                <tr>
                                    <td style="padding: 40px 40px 20px 40px; text-align: center; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); border-radius: 10px 10px 0 0;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: 600;">🎓 ClubHub</h1>
                                        <p style="margin: 10px 0 0 0; color: #e0e0e0; font-size: 14px;">Hệ thống quản lý câu lạc bộ sinh viên</p>
                                    </td>
                                </tr>
                                
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 20px 0; color: #333333; font-size: 24px; text-align: center;">
                                            🔑 Mật Khẩu Mới
                                        </h2>
                                        
                                        <p style="margin: 0 0 15px 0; color: #555555; font-size: 16px; line-height: 1.6;">
                                            Xin chào <strong>%s</strong>,
                                        </p>
                                        
                                        <p style="margin: 0 0 25px 0; color: #555555; font-size: 16px; line-height: 1.6;">
                                            Bạn đã yêu cầu đặt lại mật khẩu. Dưới đây là mật khẩu mới của bạn:
                                        </p>
                                        
                                        <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                                            <tr>
                                                <td align="center" style="padding: 20px 0;">
                                                    <div style="display: inline-block; padding: 20px 40px; background-color: #f0f4ff; border: 2px dashed #667eea; border-radius: 10px;">
                                                        <span style="font-size: 24px; font-weight: bold; color: #667eea; letter-spacing: 2px;">%s</span>
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                        
                                        <p style="margin: 25px 0 15px 0; color: #e74c3c; font-size: 14px; line-height: 1.6;">
                                            ⚠️ <strong>Bảo mật:</strong> Vui lòng đổi mật khẩu ngay sau khi đăng nhập.
                                        </p>
                                        
                                        <p style="margin: 0; color: #888888; font-size: 14px; line-height: 1.6;">
                                            Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng liên hệ với chúng tôi ngay.
                                        </p>
                                    </td>
                                </tr>
                                
                                <tr>
                                    <td style="padding: 30px 40px; background-color: #f8f9fa; border-radius: 0 0 10px 10px; text-align: center;">
                                        <p style="margin: 0; color: #888888; font-size: 12px;">
                                            © 2024 ClubHub - FPT University. All rights reserved.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(fullName, newPassword);
    }
}