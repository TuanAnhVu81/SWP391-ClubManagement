// Package định nghĩa service layer - xử lý gửi email
package com.swp391.clubmanagement.service;

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Jakarta Mail ==========
import jakarta.mail.MessagingException; // Exception khi gửi email
import jakarta.mail.internet.MimeMessage; // Email message (HTML)

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.mail.javamail.JavaMailSender; // Service gửi email (SMTP)
import org.springframework.mail.javamail.MimeMessageHelper; // Helper để tạo email HTML
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean

/**
 * Service gửi email
 * 
 * Chức năng chính:
 * - Gửi email xác thực (verification email) với link xác thực
 * - Gửi email quên mật khẩu (forgot password) với mật khẩu mới
 * 
 * Business Rules:
 * - Sử dụng HTML template để tạo email đẹp
 * - Email được gửi qua SMTP (JavaMailSender)
 * - Tất cả email đều có format HTML với styling
 * 
 * @Service: Spring Service Bean, được quản lý bởi IoC Container
 * @RequiredArgsConstructor: Lombok tự động tạo constructor inject dependencies
 * @FieldDefaults: Tự động thêm private final cho các field
 * @Slf4j: Tự động tạo logger với tên "log"
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EmailService {
    /** JavaMailSender để gửi email qua SMTP */
    JavaMailSender javaMailSender;

    /**
     * Gửi email xác thực với nút "Xác Thực Email Ngay"
     */
    public void sendVerificationEmail(String to, String fullName, String verificationLink) {
        String subject = "Xác Thực Email Của Bạn - ClubHub";
        String htmlContent = buildVerificationEmailTemplate(fullName, verificationLink);
        sendEmail(to, subject, htmlContent);
    }

    /**
     * Gửi email mật khẩu mới
     */
    public void sendForgotPasswordEmail(String to, String fullName, String newPassword) {
        String subject = "Mật Khẩu Mới - ClubHub";
        String htmlContent = buildForgotPasswordEmailTemplate(fullName, newPassword);
        sendEmail(to, subject, htmlContent);
    }

    /**
     * Gửi email HTML cơ bản
     */
    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            helper.setText(htmlContent, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("clubhubfpt@gmail.com");

            javaMailSender.send(mimeMessage);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Error sending email to {}", to, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * Template email xác thực
     * FIX: Đổi tất cả '%' trong CSS thành '%%'
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
     * Template email quên mật khẩu
     * FIX: Đổi tất cả '%' trong CSS thành '%%'
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