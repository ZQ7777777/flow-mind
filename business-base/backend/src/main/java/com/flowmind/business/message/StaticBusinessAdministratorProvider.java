package com.flowmind.business.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Simple administrator provider used by tests and fallback configuration. */
public class StaticBusinessAdministratorProvider implements BusinessAdministratorProvider {

    private final List<String> administratorUserIds;

    public StaticBusinessAdministratorProvider(String... administratorUserIds) {
        this.administratorUserIds = administratorUserIds == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(Arrays.asList(administratorUserIds));
    }

    @Override
    public List<String> listAdministratorUserIds() {
        return Collections.unmodifiableList(administratorUserIds);
    }
}