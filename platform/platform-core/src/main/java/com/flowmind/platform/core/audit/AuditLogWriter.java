package com.flowmind.platform.core.audit;

import com.flowmind.platform.persistence.entity.ProcessAuditLogEntity;

/** Unified audit log writer used by runtime, query and monitor components. */
public interface AuditLogWriter {

    ProcessAuditLogEntity append(AuditLogCommand command);
}
