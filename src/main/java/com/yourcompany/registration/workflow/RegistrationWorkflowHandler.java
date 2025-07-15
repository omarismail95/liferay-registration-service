package com.yourcompany.registration.workflow;

import com.liferay.object.model.ObjectEntry;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.workflow.BaseWorkflowHandler;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.workflow.WorkflowHandler;
import com.liferay.portal.kernel.workflow.WorkflowHandlerRegistryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.aop.AopService;

import java.io.Serializable;
import java.util.Locale;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
        "model.class.name=com.liferay.object.model.ObjectEntry"
    },
    service = WorkflowHandler.class
)
public class RegistrationWorkflowHandler
    extends BaseWorkflowHandler<ObjectEntry> {

    private static final Log _log = LogFactoryUtil.getLog(RegistrationWorkflowHandler.class);

    @Override
    public String getClassName() {
        return ObjectEntry.class.getName();
    }

    @Override
    public String getType(Locale locale) {
        return "ObjectEntry";
    }


    @Override
    public ObjectEntry updateStatus(
        int status, Map<String, Serializable> workflowContext) throws PortalException {
    _log.info(">>> updateStatus() CALLED with status: " + status);

        long userId = Long.parseLong((String) workflowContext.get(WorkflowConstants.CONTEXT_USER_ID));
        long classPK = Long.parseLong((String) workflowContext.get(WorkflowConstants.CONTEXT_ENTRY_CLASS_PK));

        ObjectEntry entry = _objectEntryLocalService.getObjectEntry(classPK);

        if (status == WorkflowConstants.STATUS_APPROVED) {
            _log.info(">>> Entry approved, creating Liferay user...");

            Map<String, Serializable> values = entry.getValues();
            String name = (String) values.get("name");
            String email = (String) values.get("email");

            if (_userLocalService.fetchUserByEmailAddress(entry.getCompanyId(), email) == null) {
                User newUser = _userLocalService.addUser(
                userId,
                entry.getCompanyId(),
                true, // autoPassword
                "", "", // password1, password2
                true, // autoScreenName
                "", // screenName
                email,
                Locale.getDefault(),
                name, "", "", // firstName, middleName, lastName
                0L, 0L, // prefixId, suffixId
                true, // male
                1, 1, 1970, // birthdate
                "Registered User", // jobTitle
                0, //
                new long[0], new long[0], new long[0], new long[0], // groupIds, orgIds, roleIds, userGroupIds
                false, // sendEmail
                new ServiceContext()
            );
                _log.info(">>> User created: " + email);
            } else {
                _log.warn(">>> User with email already exists: " + email);
            }
        }

        return entry;
    }

    @Reference
    private com.liferay.object.service.ObjectEntryLocalService _objectEntryLocalService;

    @Reference
    private UserLocalService _userLocalService;
}