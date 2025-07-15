package com.yourcompany.registration;

import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryLocalServiceWrapper;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.util.GetterUtil;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;

@Component(
    immediate = true,
    property = {
        "service.wrapper.class=com.liferay.object.service.ObjectEntryLocalService",
        "service.ranking:Integer=1000"
    },
    service = ServiceWrapper.class
)
public class RegistrationObjectServiceWrapper extends ObjectEntryLocalServiceWrapper {

    private static final Log _log = LogFactoryUtil.getLog(RegistrationObjectServiceWrapper.class);

    public RegistrationObjectServiceWrapper() {
        super(null);
        _log.info(">>> Constructor hit for RegistrationObjectServiceWrapper");
    }

    @Reference(unbind = "-")
    public void setWrappedObjectEntryLocalService(ObjectEntryLocalService objectEntryLocalService) {
        super.setWrappedService(objectEntryLocalService);
        _log.info(">>> ObjectEntryLocalService injected into RegistrationObjectServiceWrapper");
    }

    @Reference
    private DLAppLocalService _dlAppLocalService;

    @Override
public ObjectEntry addObjectEntry(
        long userId, long groupId, long objectDefinitionId,
        Map<String, Serializable> values, ServiceContext serviceContext)
        throws PortalException {

    _log.info(">>> Intercepted addObjectEntry");

    try {
        _log.info("Initial values: " + values);

        Object fileField = values.get("upload");
        if (fileField == null) {
            _log.warn("Upload field is null, skipping document ID extraction.");
            return super.addObjectEntry(userId, groupId, objectDefinitionId, values, serviceContext);
        }

        long fileEntryId;
        if (fileField instanceof Long) {
            fileEntryId = (Long) fileField;
        } else if (fileField instanceof Map) {
            fileEntryId = GetterUtil.getLong(((Map<?, ?>) fileField).get("classPK"));
        } else {
            _log.warn("Unexpected upload field type: " + fileField.getClass().getName());
            return super.addObjectEntry(userId, groupId, objectDefinitionId, values, serviceContext);
        }

        _log.info("Got fileEntryId: " + fileEntryId);

        FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);
        try (InputStream inputStream = fileEntry.getContentStream()) {
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            _log.info("Extracted text: \n" + text);

            String documentId = extractDocumentId(text) + "-" + System.currentTimeMillis();
            _log.info("Extracted document ID: " + documentId);

            // Add BEFORE the call
            values.put("documentID", documentId);
        }
    } catch (Exception e) {
        _log.error("Error processing document ID extraction in wrapper", e);
    }

    ObjectEntry objectEntry = super.addObjectEntry(userId, groupId, objectDefinitionId, values, serviceContext);
    _log.info("Final ObjectEntry values (post-add): " + objectEntry.getValues());
    return objectEntry;
}


    private String extractDocumentId(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?i)document\\s*id\\s*[:\\-]?\\s*([A-Z0-9]{6,})")
            .matcher(text);
        return matcher.find() ? matcher.group(1) : "NOT_FOUND";
    }
}
