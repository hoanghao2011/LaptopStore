package com.laptopshop.config;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final ConcurrentHashMap<String, RequestInfo> requestCounts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 5; // Giới hạn 5 yêu cầu
    private static final long TIME_WINDOW = 60_000; // 60 giây

    private static class RequestInfo {
        AtomicInteger count = new AtomicInteger(0);
        long windowStart = System.currentTimeMillis();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // ✅ Log để xác minh filter chạy
        System.out.println("📥 Filter triggered - URI: " + uri + ", IP: " + clientIp + ", Method: " + method);

        // Danh sách endpoint cần áp dụng Rate Limiter (mở rộng tùy ý)
        if ((uri.equals("/laptopshop/api/san-pham/all") || uri.equals("/laptopshop/api/don-hang/all")
                || uri.equals("/laptopshop/api/danh-muc/all")) && method.equalsIgnoreCase("GET")) {

            RequestInfo info = requestCounts.computeIfAbsent(clientIp, k -> new RequestInfo());
            long now = System.currentTimeMillis();

            // Kiểm tra và reset window
            if (now - info.windowStart > TIME_WINDOW) {
                synchronized (info) {
                    if (now - info.windowStart > TIME_WINDOW) {
                        info.count.set(0);
                        info.windowStart = now;
                    }
                }
            }

            // Tăng đếm và kiểm tra giới hạn
            int current = info.count.incrementAndGet();
            System.out.println("➡ Count for " + clientIp + ": " + current);

            if (current > MAX_REQUESTS) {
                System.out.println(" Rate limit exceeded for " + clientIp);
                response.setStatus(429); // Too Many Requests
                response.getWriter().write("Rate limit exceeded. Try again later.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // Dọn dẹp dữ liệu cũ định kỳ (có thể gọi từ scheduler nếu cần)
    public void cleanupOldData() {
        long now = System.currentTimeMillis();
        requestCounts.entrySet().removeIf(entry -> now - entry.getValue().windowStart > TIME_WINDOW);
    }
}