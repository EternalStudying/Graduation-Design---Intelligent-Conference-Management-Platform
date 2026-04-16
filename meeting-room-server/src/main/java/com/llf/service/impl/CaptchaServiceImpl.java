package com.llf.service.impl;

import com.llf.auth.CaptchaStore;
import com.llf.result.BizException;
import com.llf.result.ErrorCode;
import com.llf.service.CaptchaService;
import com.llf.vo.auth.CaptchaVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final String CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CAPTCHA_LENGTH = 4;
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;

    @Resource
    private CaptchaStore captchaStore;

    @Override
    public CaptchaVO createCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = randomCode();
        captchaStore.save(captchaId, code, CAPTCHA_TTL);

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(captchaId);
        vo.setImageBase64("data:image/png;base64," + renderBase64Png(code));
        return vo;
    }

    @Override
    public void verifyCaptcha(String captchaId, String code) {
        CaptchaStore.VerifyResult result = captchaStore.verifyAndConsume(captchaId, code);
        if (result.success()) {
            return;
        }
        if (result.expired()) {
            throw new BizException(ErrorCode.CAPTCHA_EXPIRED);
        }
        throw new BizException(ErrorCode.CAPTCHA_INVALID);
    }

    private String randomCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            sb.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return sb.toString();
    }

    private String renderBase64Png(String code) {
        try {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                paintBackground(g);
                paintNoise(g);
                paintCode(g, code);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "captcha image generation failed");
        }
    }

    private void paintBackground(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(225, 232, 238));
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
    }

    private void paintNoise(Graphics2D g) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 0; i < 10; i++) {
            g.setColor(randomColor(160, 220));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        for (int i = 0; i < 40; i++) {
            g.setColor(randomColor(120, 200));
            g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }
    }

    private void paintCode(Graphics2D g, String code) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        FontMetrics metrics = g.getFontMetrics();
        int charWidth = WIDTH / (code.length() + 1);

        for (int i = 0; i < code.length(); i++) {
            int x = 14 + i * charWidth;
            int y = 10 + metrics.getAscent() + random.nextInt(6);
            double angle = (random.nextDouble() - 0.5D) * 0.45D;

            g.setColor(randomColor(20, 130));
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }
    }

    private Color randomColor(int min, int max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Color(
                random.nextInt(min, max + 1),
                random.nextInt(min, max + 1),
                random.nextInt(min, max + 1)
        );
    }
}
