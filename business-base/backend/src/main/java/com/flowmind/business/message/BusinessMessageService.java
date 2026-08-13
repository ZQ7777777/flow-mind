package com.flowmind.business.message;

import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessMessageService {

    private final BusinessUserMessageRepository repository;
    private final CurrentBusinessUserProvider currentUserProvider;

    public BusinessMessageService(BusinessUserMessageRepository repository,
                                  CurrentBusinessUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    public BusinessMessagePageResponse query(BusinessMessageQuery query) {
        BusinessMessageQuery normalized = query == null ? new BusinessMessageQuery() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());
        String currentUserId = currentUserId();
        List<BusinessMessageResponse> records = new ArrayList<BusinessMessageResponse>();
        for (BusinessUserMessageEntity entity : repository.query(currentUserId, normalized.getReadStatus(),
                normalized.getMessageType(), pageSize, (pageNo - 1) * pageSize)) {
            records.add(BusinessMessageResponse.from(entity));
        }
        BusinessMessagePageResponse response = new BusinessMessagePageResponse();
        response.setRecords(records);
        response.setPageNo(Integer.valueOf(pageNo));
        response.setPageSize(Integer.valueOf(pageSize));
        response.setUnreadCount(Long.valueOf(repository.countUnread(currentUserId)));
        return response;
    }

    public BusinessUnreadCountResponse unreadCount() {
        return new BusinessUnreadCountResponse(Long.valueOf(repository.countUnread(currentUserId())));
    }

    public void markRead(String messageId) {
        repository.markRead(messageId, currentUserId(), LocalDateTime.now());
    }

    public void markAllRead() {
        repository.markAllRead(currentUserId(), LocalDateTime.now());
    }

    private String currentUserId() {
        return currentUserProvider.currentUser().getUserId();
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo.intValue() < 1 ? 1 : pageNo.intValue();
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize.intValue() < 1) {
            return 20;
        }
        return Math.min(pageSize.intValue(), 100);
    }
}