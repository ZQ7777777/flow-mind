package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.request.StoreFileRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicSpiContractTest {

    @Test
    void fileStorageProviderUsesOnlyPublicApiContracts() throws NoSuchMethodException {
        Method store = FileStorageProvider.class.getMethod("store", StoreFileRequest.class);
        Method load = FileStorageProvider.class.getMethod("load", String.class);
        Method delete = FileStorageProvider.class.getMethod("delete", String.class);

        assertEquals(StoredFile.class, store.getReturnType());
        assertEquals(FileContent.class, load.getReturnType());
        assertEquals(Void.TYPE, delete.getReturnType());
        assertPublicApiTypes(store);
        assertPublicApiTypes(load);
        assertPublicApiTypes(delete);
    }

    @Test
    void messagePublisherUsesPublicMessageContract() throws NoSuchMethodException {
        Method publish = MessagePublisher.class.getMethod("publish", ProcessMessage.class);

        assertEquals(Void.TYPE, publish.getReturnType());
        assertPublicApiTypes(publish);
    }

    @Test
    void organizationProviderUsesOnlyPublicApiContracts() throws NoSuchMethodException {
        Method listUsersByDepartment = OrganizationProvider.class.getMethod("listUsersByDepartment", String.class);
        Method listUsersByRole = OrganizationProvider.class.getMethod("listUsersByRole", String.class);
        Method listUsersByRoleAndDepartment = OrganizationProvider.class.getMethod(
                "listUsersByRoleAndDepartment", String.class, String.class);
        Method findUser = OrganizationProvider.class.getMethod("findUser", String.class);

        assertEquals(List.class, listUsersByDepartment.getReturnType());
        assertEquals(List.class, listUsersByRole.getReturnType());
        assertEquals(List.class, listUsersByRoleAndDepartment.getReturnType());
        assertEquals(Optional.class, findUser.getReturnType());
        assertPublicApiTypes(listUsersByDepartment);
        assertPublicApiTypes(listUsersByRole);
        assertPublicApiTypes(listUsersByRoleAndDepartment);
        assertPublicApiTypes(findUser);
    }

    @Test
    void approverResolverUsesPublicRequestAndUserContract() throws NoSuchMethodException {
        Method resolve = ApproverResolver.class.getMethod("resolveApprovers", ApproverResolveRequest.class);

        assertEquals(List.class, resolve.getReturnType());
        assertEquals(ApproverResolveRequest.class, resolve.getParameterTypes()[0]);
        assertPublicApiTypes(resolve);
        assertFalse(isPersistenceType(UserDTO.class), "UserDTO must remain a public API type");
    }

    private void assertPublicApiTypes(Method method) {
        assertFalse(isPersistenceType(method.getReturnType()), method + " exposes a persistence return type");
        for (Class<?> parameterType : method.getParameterTypes()) {
            assertFalse(isPersistenceType(parameterType), method + " exposes a persistence parameter type");
        }
    }

    private boolean isPersistenceType(Class<?> type) {
        Package typePackage = type.getPackage();
        return typePackage != null && typePackage.getName().startsWith("com.flowmind.platform.persistence");
    }
}
