package com.flowmind.business.message;

import java.util.List;

/** Resolves business administrators that should receive platform alert messages. */
public interface BusinessAdministratorProvider {

    List<String> listAdministratorUserIds();
}