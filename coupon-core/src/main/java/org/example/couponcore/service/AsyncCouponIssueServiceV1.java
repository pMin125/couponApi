package org.example.couponcore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.C;
import org.example.couponcore.component.DistributeLockExecutor;
import org.example.couponcore.exception.CouponIssueException;
import org.example.couponcore.exception.ErrorCode;
import org.example.couponcore.model.Coupon;
import org.example.couponcore.repository.redis.RedisRepository;
import org.example.couponcore.repository.redis.dto.CouponIssueRequest;
import org.example.couponcore.repository.redis.dto.CouponRedisEntity;
import org.springframework.stereotype.Service;

import static org.example.couponcore.exception.ErrorCode.*;
import static org.example.couponcore.util.CouponRedisUtils.getIssueRequestKey;
import static org.example.couponcore.util.CouponRedisUtils.getIssueRequestQueueKey;

@RequiredArgsConstructor
@Service
public class AsyncCouponIssueServiceV1 {
    private final RedisRepository redisRepository;
    private final CouponIssueRedisService couponIssueRedisService;
    private final DistributeLockExecutor distributeLockExecutor;
    private final CouponCacheService couponCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public void issue(long couponId, long userId) {
        CouponRedisEntity coupon = couponCacheService.getCouponCache(couponId);
        coupon.checkIssuableCoupon();
        distributeLockExecutor.execute("lock_%s".formatted(couponId), 3000, 3000, () -> {
            couponIssueRedisService.checkCouponIssueQuantity(coupon,userId);
            issueRequest(couponId, userId);
        });
    }

    private void issueRequest(long couponId, long userId){
        CouponIssueRequest issueRequest = new CouponIssueRequest(couponId,userId);
        try {
            String value = objectMapper.writeValueAsString(issueRequest);
            //요청 발급수량에 대한 제어
            redisRepository.sAdd(getIssueRequestKey(couponId),String.valueOf(userId));
            //쿠폰 발급 큐 구현
            redisRepository.rPush(getIssueRequestQueueKey(),value);
        } catch (JsonProcessingException e) {
            throw new CouponIssueException(FAIL_COUPON_ISSUE_REQUEST, "input: %s".formatted(issueRequest));
        }
    }
}
