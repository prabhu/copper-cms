/**
 * Copyright 2017 Pogeyan Technologies
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.pogeyan.cmis.actors;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.data.BulkUpdateObjectIdAndChangeToken;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.FailedToDeleteData;
import org.apache.chemistry.opencmis.commons.data.ObjectData;
import org.apache.chemistry.opencmis.commons.data.Properties;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.data.RenditionData;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.DateTimeFormat;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConstraintException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNotSupportedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStorageException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUpdateConflictException;
import org.apache.chemistry.opencmis.commons.impl.JSONConverter;
import org.apache.chemistry.opencmis.commons.impl.ReturnVersion;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.BulkUpdateObjectIdAndChangeTokenImpl;
import org.apache.chemistry.opencmis.commons.impl.json.JSONArray;
import org.apache.chemistry.opencmis.commons.impl.json.JSONObject;
import org.apache.chemistry.opencmis.commons.spi.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pogeyan.cmis.api.BaseClusterActor;
import com.pogeyan.cmis.api.BaseRequest;
import com.pogeyan.cmis.api.BaseResponse;
import com.pogeyan.cmis.api.auth.IUserGroupObject;
import com.pogeyan.cmis.api.data.IBaseObject;
import com.pogeyan.cmis.api.messages.CmisBaseResponse;
import com.pogeyan.cmis.api.messages.PostFileResponse;
import com.pogeyan.cmis.api.messages.PostRequest;
import com.pogeyan.cmis.api.messages.QueryGetRequest;
import com.pogeyan.cmis.api.utils.Helpers;
import com.pogeyan.cmis.api.utils.MetricsInputs;
import com.pogeyan.cmis.impl.services.CmisObjectService;
import com.pogeyan.cmis.impl.services.CmisTypeCacheService;
import com.pogeyan.cmis.impl.services.CmisVersioningServices;
import com.pogeyan.cmis.impl.utils.CmisPropertyConverter;
import com.pogeyan.cmis.impl.utils.CmisUtils;
import com.pogeyan.cmis.impl.utils.DBUtils;

public class ObjectActor extends BaseClusterActor<BaseRequest, BaseResponse> {
	private static final Logger LOG = LoggerFactory.getLogger(ObjectActor.class);
	private static final String ROOT = "@ROOT@";

	@Override
	public String getName() {
		return "object";
	}

	public ObjectActor() {

		this.registerMessageHandle("object".toLowerCase(), QueryGetRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.getObject((QueryGetRequest) t))));

		this.registerMessageHandle("properties".toLowerCase(), QueryGetRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.getProperties((QueryGetRequest) t))));

		this.registerMessageHandle("allowableActions", QueryGetRequest.class, (t, b) -> CompletableFuture.supplyAsync(
				() -> CmisBaseResponse.fromWithTryCatch(() -> this.getAllowableActions((QueryGetRequest) t))));

		this.registerMessageHandle("renditions", QueryGetRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.getRenditions((QueryGetRequest) t))));

		this.registerMessageHandle("createFolder", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.createFolders((PostRequest) t))));

		this.registerMessageHandle("createDocument", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.createDocument((PostRequest) t))));

		this.registerMessageHandle("createDocumentFromSource", PostRequest.class,
				(t, b) -> CompletableFuture.supplyAsync(
						() -> CmisBaseResponse.fromWithTryCatch(() -> this.createDocumentFromSource((PostRequest) t))));

		this.registerMessageHandle("createItem", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.createItem((PostRequest) t))));

		this.registerMessageHandle("createPolicy", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.createPolicy((PostRequest) t))));

		this.registerMessageHandle("createRelationship", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.createRelationship((PostRequest) t))));

		this.registerMessageHandle("bulkUpdate", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.bulkUpdate((PostRequest) t))));

		this.registerMessageHandle("update", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.update((PostRequest) t))));

		this.registerMessageHandle("content", QueryGetRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.getContent((QueryGetRequest) t))));

		this.registerMessageHandle("setContent", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.setContent((PostRequest) t))));

		this.registerMessageHandle("appendContent", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.appendContent((PostRequest) t))));

		this.registerMessageHandle("deleteContent", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> this.deleteContent((PostRequest) t))));

		this.registerMessageHandle("delete", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> ObjectActor.delete((PostRequest) t))));

		this.registerMessageHandle("deleteTree", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> ObjectActor.deleteTree((PostRequest) t))));

		this.registerMessageHandle("move", PostRequest.class, (t, b) -> CompletableFuture
				.supplyAsync(() -> CmisBaseResponse.fromWithTryCatch(() -> ObjectActor.moveObject((PostRequest) t))));

	}

	private JSONObject getObject(QueryGetRequest t) throws CmisInvalidArgumentException, IllegalArgumentException,
			CmisRuntimeException, CmisObjectNotFoundException {
		String permission = t.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "get")) {
			throw new CmisRuntimeException(t.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = t.getObjectId();
		String[] options = { t.getParameter("typeId") };
		boolean acessPermission = false;
		IBaseObject data = DBUtils.BaseDAO.getByObjectId(t.getRepositoryId(), objectId, options);
		acessPermission = CmisObjectService.Impl.getAclAccess(t.getRepositoryId(), data, t.getUserObject());
		if (data != null && !data.getName().equals(ROOT) && acessPermission == false) {
			throw new CmisInvalidArgumentException(
					"{} does not have valid acces control permission to access this object", t.getUserName());
		}

		ReturnVersion returnVersion = t.getEnumParameter(QueryGetRequest.PARAM_RETURN_VERSION, ReturnVersion.class);
		String filter = t.getParameter(QueryGetRequest.PARAM_FILTER);
		Boolean includeAllowableActions = t.getBooleanParameter(QueryGetRequest.PARAM_ALLOWABLE_ACTIONS);
		IncludeRelationships includeRelationships = t.getEnumParameter(QueryGetRequest.PARAM_RELATIONSHIPS,
				IncludeRelationships.class);
		String renditionFilter = t.getParameter(QueryGetRequest.PARAM_RENDITION_FILTER);
		Boolean includePolicyIds = t.getBooleanParameter(QueryGetRequest.PARAM_POLICY_IDS);
		Boolean includeAcl = t.getBooleanParameter(QueryGetRequest.PARAM_ACL);
		boolean succinct = t.getBooleanParameter(QueryGetRequest.PARAM_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = t.getDateTimeFormatParameter();
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}, version: {}", "getObject",
				objectId, t.getRepositoryId(), returnVersion);
		ObjectData object = null;
		if (returnVersion == ReturnVersion.LATEST || returnVersion == ReturnVersion.LASTESTMAJOR) {
			object = CmisVersioningServices.Impl.getObjectOfLatestVersion(t.getRepositoryId(), objectId, null,
					returnVersion == ReturnVersion.LASTESTMAJOR, filter, includeAllowableActions, null,
					includePolicyIds, includeAcl, null, null, t.getUserObject());
		} else {
			object = CmisObjectService.Impl.getObject(t.getRepositoryId(), objectId, filter, includeAllowableActions,
					includeRelationships, renditionFilter, includePolicyIds, includeAcl, null, t.getUserObject(),
					t.getBaseTypeId(), options);
		}
		JSONObject result = JSONConverter.convert(object, CmisTypeCacheService.get(t.getRepositoryId()),
				JSONConverter.PropertyMode.OBJECT, succinct, dateTimeFormat);

		return result;
	}

	private JSONObject getProperties(QueryGetRequest t)
			throws CmisInvalidArgumentException, IllegalArgumentException, CmisRuntimeException {
		String permission = t.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "get")) {
			throw new CmisRuntimeException(t.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = t.getObjectId();
		ReturnVersion returnVersion = t.getEnumParameter(QueryGetRequest.PARAM_RETURN_VERSION, ReturnVersion.class);
		String filter = t.getParameter(QueryGetRequest.PARAM_FILTER);
		boolean succinct = t.getBooleanParameter(QueryGetRequest.PARAM_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = t.getDateTimeFormatParameter();
		LOG.info("Method name: {}, getting properties using this id: {}, repositoryId: {}, version: {}", "getObject",
				objectId, t.getRepositoryId(), returnVersion);
		if (returnVersion == ReturnVersion.LATEST || returnVersion == ReturnVersion.LASTESTMAJOR) {
			// TODO: implement version
			return null;
		} else {
			ObjectData object = CmisObjectService.Impl.getObject(t.getRepositoryId(), objectId, filter, true,
					IncludeRelationships.NONE, "cmis:none", false, false, null, t.getUserObject(), t.getBaseTypeId(),
					null);
			Properties properties = object.getProperties();
			if (properties == null) {
				throw new CmisRuntimeException("Properties are null!");
			}
			JSONObject result = JSONConverter.convert(properties, objectId.toString(),
					CmisTypeCacheService.get(t.getRepositoryId()), JSONConverter.PropertyMode.CHANGE, succinct,
					dateTimeFormat);
			return result;
		}

	}

	private JSONObject getAllowableActions(QueryGetRequest t) throws CmisRuntimeException {
		String permission = t.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "get")) {
			throw new CmisRuntimeException(t.getUserName() + " is not authorized to applyAcl.");
		}
		LOG.info("Method name: {}, getting allowable actions using this id: {}, repositoryId: {}",
				"getAllowableActions", t.getObjectId(), t.getRepositoryId());
		AllowableActions allowableActions = CmisObjectService.Impl.getAllowableActions(t.getRepositoryId(), null,
				t.getObjectId(), t.getUserObject().getUserDN());
		JSONObject resultAllowableActions = JSONConverter.convert(allowableActions);
		return resultAllowableActions;
	}

	private JSONArray getRenditions(QueryGetRequest t) throws CmisInvalidArgumentException, CmisRuntimeException {
		String permission = t.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "get")) {
			throw new CmisRuntimeException(t.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = t.getObjectId();
		String renditionFilter = t.getParameter(QueryGetRequest.PARAM_RENDITION_FILTER);
		BigInteger maxItems = t.getBigIntegerParameter(QueryGetRequest.PARAM_MAX_ITEMS);
		BigInteger skipCount = t.getBigIntegerParameter(QueryGetRequest.PARAM_SKIP_COUNT);
		LOG.info("Method name: {}, getting renditions using this id: {}, repositoryId: {}", "getRenditions", objectId,
				t.getRepositoryId());
		List<RenditionData> renditions = CmisObjectService.Impl.getRenditions(t.getRepositoryId(), null, objectId,
				renditionFilter, maxItems, skipCount, null);
		JSONArray resultRenditions = new JSONArray();
		if (renditions != null) {
			for (RenditionData rendition : renditions) {
				resultRenditions.add(JSONConverter.convert(rendition));
			}
		}
		return resultRenditions;

	}

	private JSONObject createFolders(PostRequest request) throws CmisObjectNotFoundException, IllegalArgumentException,
			CmisInvalidArgumentException, CmisRuntimeException {
		String folderId = request.getObjectId() != null ? request.getObjectId() : null;
		String permission = request.getUserObject().getPermission();
		String principalId = request.getUserObject().getUserDN();
		IUserGroupObject[] groupsId = request.getUserObject().getGroups();
		if (!Helpers.getGroupPermission(permission, groupsId)) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Properties prop = CmisPropertyConverter.Impl.createNewProperties(request.getPropertyData(),
				request.getRepositoryId(), request.getUserObject());
		Acl aclImp = CmisUtils.Object.getAcl(request.getAddAcl(), principalId, permission);
		LOG.info("Method name: {}, creating folder using this id: {} , repository: {}", "createFolder", folderId,
				request.getRepositoryId());
		String newObjectId = CmisObjectService.Impl.createFolder(request.getRepositoryId(), folderId, prop,
				request.getPolicies(), aclImp, request.getRemoveAcl(), request.getUserObject());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_FOLDER, null);
		if (object == null) {
			throw new CmisRuntimeException("New folder is null!");
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("createFolder complete with id: {}", object.getId());
		}
		JSONObject jsonObject = JSONConverter.convert(object, CmisTypeCacheService.get(request.getRepositoryId()),
				JSONConverter.PropertyMode.CHANGE, succinct, dateTimeFormat);
		return jsonObject;

	}

	private JSONObject createDocument(PostRequest request) throws CmisInvalidArgumentException, CmisConstraintException,
			IllegalArgumentException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		String principalId = request.getUserObject().getUserDN();
		IUserGroupObject[] groupsId = request.getUserObject().getGroups();
		if (!Helpers.getGroupPermission(permission, groupsId)) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}

		String folderId = request.getObjectId() != null ? request.getObjectId() : null;
		VersioningState versioningState = request.getEnumParameter(QueryGetRequest.PARAM_VERSIONIG_STATE,
				VersioningState.class);
		// String token = request.getParameter(request.PARAM_TOKEN);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Properties prop = CmisPropertyConverter.Impl.createNewProperties(request.getPropertyData(),
				request.getRepositoryId(), request.getUserObject());
		Acl aclImp = CmisUtils.Object.getAcl(request.getAddAcl(), principalId, permission);
		String newObjectId = null;
		LOG.info("Method name: {}, creating document under this folder: {} , repository: {}", "createDocument",
				folderId, request.getRepositoryId());
		if (request.getContentStream() == null) {
			newObjectId = CmisObjectService.Impl.createDocument(request.getRepositoryId(), prop, folderId, null,
					versioningState, request.getPolicies(), aclImp, request.getRemoveAcl(), request.getUserObject());
		} else {
			newObjectId = CmisObjectService.Impl.createDocument(request.getRepositoryId(), prop, folderId,
					request.getContentStream(), versioningState, request.getPolicies(), aclImp, request.getRemoveAcl(),
					request.getUserObject());
		}
		LOG.info("Method name:{}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_DOCUMENT, null);
		if (object == null) {
			MetricsInputs.markUploadErrorMeter();
			throw new CmisRuntimeException("New document is null!");
		}

		// return object
		JSONObject jsonObject = JSONConverter.convert(object, CmisTypeCacheService.get(request.getRepositoryId()),
				JSONConverter.PropertyMode.CHANGE, succinct, dateTimeFormat);
		return jsonObject;

	}

	private JSONObject createDocumentFromSource(PostRequest request) throws CmisInvalidArgumentException,
			CmisConstraintException, CmisObjectNotFoundException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		String principalId = request.getUserObject().getUserDN();
		IUserGroupObject[] groups = request.getUserObject().getGroups();
		if (!Helpers.getGroupPermission(permission, groups)) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String folderId = request.getObjectId() != null ? request.getObjectId() : null;
		VersioningState versioningState = request.getEnumParameter(QueryGetRequest.PARAM_VERSIONIG_STATE,
				VersioningState.class);
		// String token = request.getParameter(request.PARAM_TOKEN);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		String sourceId = request.getParameter(QueryGetRequest.PARAM_SOURCE_ID);
		Acl aclImp = CmisUtils.Object.getAcl(request.getAddAcl(), principalId, permission);
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", sourceId,
				request.getRepositoryId());
		ObjectData sourceDoc = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), sourceId,
				request.getUserObject(), BaseTypeId.CMIS_DOCUMENT, null);
		PropertyData<?> sourceTypeId = sourceDoc.getProperties().getProperties().get(PropertyIds.OBJECT_TYPE_ID);
		if (sourceTypeId == null || sourceTypeId.getFirstValue() == null) {
			throw new CmisRuntimeException("Source object has no type!?!");
		}
		Properties prop = CmisPropertyConverter.Impl.createNewProperties(request.getPropertyData(),
				request.getRepositoryId(), request.getUserObject());
		LOG.info("Method name: {}, creating document under this folder: {} , repository: {}",
				"createDocumentFromSource", folderId, request.getRepositoryId());
		String newObjectId = CmisObjectService.Impl.createDocumentFromSource(request.getRepositoryId(), sourceId, prop,
				folderId, versioningState, request.getPolicies(), aclImp, request.getRemoveAcl(),
				request.getUserObject());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_DOCUMENT, null);
		if (object == null) {
			throw new CmisRuntimeException("New document is null!");
		}

		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;

	}

	private JSONObject createItem(PostRequest request) throws CmisInvalidArgumentException, CmisObjectNotFoundException,
			IllegalArgumentException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		String principalId = request.getUserObject().getUserDN();
		String[] options = { request.getParameter("typeId") };
		IUserGroupObject[] groups = request.getUserObject().getGroups();
		if (!Helpers.getGroupPermission(permission, groups)) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}

		String folderId = request.getObjectId() != null ? request.getObjectId() : null;
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Properties prop = CmisPropertyConverter.Impl.createNewProperties(request.getPropertyData(),
				request.getRepositoryId(), request.getUserObject());
		Acl aclImp = CmisUtils.Object.getAcl(request.getAddAcl(), principalId, permission);
		LOG.info("Method name: {}, creating item under this folder: {}, repository: {}", "createItem", folderId,
				request.getRepositoryId());
		String newObjectId = CmisObjectService.Impl.createItem(request.getRepositoryId(), prop, folderId,
				request.getPolicies(), aclImp, request.getRemoveAcl(), request.getUserObject());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_ITEM, options);
		if (object == null) {
			throw new CmisRuntimeException("New folder is null!");
		}
		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;

	}

	private JSONObject createPolicy(PostRequest request) throws CmisInvalidArgumentException,
			CmisObjectNotFoundException, IllegalArgumentException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		String principalId = request.getUserObject().getUserDN();
		IUserGroupObject[] groups = request.getUserObject().getGroups();
		if (!Helpers.getGroupPermission(permission, groups)) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String folderId = request.getObjectId() != null ? request.getObjectId() : null;
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Properties prop = CmisPropertyConverter.Impl.createNewProperties(request.getPropertyData(),
				request.getRepositoryId(), request.getUserObject());
		Acl aclImp = CmisUtils.Object.getAcl(request.getAddAcl(), principalId, permission);
		LOG.info("Method name: {}, creating policy under this folder: {}, repository: {}", "createPolicy", folderId,
				request.getRepositoryId());
		String newObjectId = CmisObjectService.Impl.createPolicy(request.getRepositoryId(), prop, folderId,
				request.getPolicies(), aclImp, request.getRemoveAcl(), request.getUserObject());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_POLICY, null);
		if (object == null) {
			throw new CmisRuntimeException("New folder is null!");
		}
		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;

	}

	private JSONObject createRelationship(PostRequest request) throws CmisInvalidArgumentException,
			CmisObjectNotFoundException, IllegalArgumentException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		String principalId = request.getUserObject().getUserDN();
		IUserGroupObject[] groups = request.getUserObject().getGroups();
		if (!Helpers.getGroupPermission(permission, groups)) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Properties prop = CmisPropertyConverter.Impl.createNewProperties(request.getPropertyData(),
				request.getRepositoryId(), request.getUserObject());
		String folderId = request.getObjectId() != null ? request.getObjectId() : null;
		Acl aclImp = CmisUtils.Object.getAcl(request.getAddAcl(), principalId, permission);
		LOG.info("Method name: {}, creating realtionship under this folder: {}, repository: {}", "createRelationship",
				folderId, request.getRepositoryId());
		String newObjectId = CmisObjectService.Impl.createRelationship(request.getRepositoryId(), folderId, prop,
				request.getPolicies(), aclImp, request.getRemoveAcl(), request.getUserObject());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_RELATIONSHIP, null);
		if (object == null) {
			throw new CmisRuntimeException("New folder is null!");
		}
		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;

	}

	private JSONArray bulkUpdate(PostRequest request) throws CmisInvalidArgumentException, CmisRuntimeException {
		// get object ids and change tokens
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		List<BulkUpdateObjectIdAndChangeToken> objectIdAndChangeToken = new ArrayList<BulkUpdateObjectIdAndChangeToken>();
		List<String> objectIds = request.getObjectIds();
		List<String> changeTokens = request.getChangeTokens();

		if (CmisPropertyConverter.Impl.isNullOrEmpty(objectIds)) {
			throw new CmisInvalidArgumentException("No object ids provided!");
		}

		int n = objectIds.size();
		for (int i = 0; i < n; i++) {
			String id = objectIds.get(i);
			String changeToken = (changeTokens != null && changeTokens.size() > i ? changeTokens.get(i) : null);
			if (changeToken != null && changeToken.length() == 0) {
				changeToken = null;
			}
			objectIdAndChangeToken.add(new BulkUpdateObjectIdAndChangeTokenImpl(id, changeToken));
		}

		// get secondary type ids
		List<String> addSecondaryTypes = request.getAddSecondaryTypes();
		List<String> removeSecondaryTypes = request.getRemoveSecondaryTypes();
		LOG.info("Method name: {}, getting the properties from this ids: {}, repositoryId: {}",
				"createUpdateProperties", objectIds, request.getRepositoryId());
		Properties properties = CmisPropertyConverter.Impl.createUpdateProperties(request.getPropertyData(), null,
				addSecondaryTypes, objectIds, request.getRepositoryId(), null, request.getUserObject());
		LOG.info("Method name: {}, update properties uisng this list of objectIds: {}, repositoryId: {}",
				"bulkUpdateProperties", objectIdAndChangeToken, request.getRepositoryId());
		List<BulkUpdateObjectIdAndChangeToken> result = CmisObjectService.Impl.bulkUpdateProperties(
				request.getRepositoryId(), objectIdAndChangeToken, properties, addSecondaryTypes, removeSecondaryTypes,
				null, request.getUserObject());

		// return result
		JSONArray jsonList = new JSONArray();
		if (result != null) {
			for (BulkUpdateObjectIdAndChangeToken oc : result) {
				if (oc != null) {
					jsonList.add(JSONConverter.convert(oc));
				}
			}
		}
		return jsonList;

	}

	@SuppressWarnings("unused")
	private JSONObject update(PostRequest request) throws CmisRuntimeException, CmisObjectNotFoundException,
			CmisRuntimeException, CmisUpdateConflictException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		IBaseObject data = DBUtils.BaseDAO.getByObjectId(request.getRepositoryId(), objectId, null);
		String typeId = CmisPropertyConverter.Impl.getTypeIdForObject(request.getRepositoryId(), objectId);
		String changeToken = request.getParameter(QueryGetRequest.CONTROL_CHANGE_TOKEN);
		String token = request.getParameter(QueryGetRequest.PARAM_TOKEN);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Holder<String> objectIdHolder = new Holder<String>(objectId.toString());
		Holder<String> changeTokenHolder = (changeToken == null ? null : new Holder<String>(changeToken));
		LOG.info("Method name: {}, creating the properties using this object data: {}, repositoryId: {}",
				"createUpdateProperties", data.getId().toString(), request.getRepositoryId());
		Properties properties = CmisPropertyConverter.Impl.createUpdateProperties(request.getPropertyData(), typeId,
				null, Collections.singletonList(objectId.toString()), request.getRepositoryId(), data,
				request.getUserObject());
		LOG.info("Method name: {}, update the object properties using this id: {}, repositoryId: {}",
				"updateProperties", objectIdHolder, request.getRepositoryId());
		CmisObjectService.Impl.updateProperties(request.getRepositoryId(), objectIdHolder, changeTokenHolder,
				properties, null, null, request.getUserObject());
		String newObjectId = (objectIdHolder.getValue() == null ? objectId : objectIdHolder.getValue());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), request.getBaseTypeId(), null);
		if (object == null) {
			throw new CmisRuntimeException("Object is null!");
		}

		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;
	}

	private PostFileResponse getContent(QueryGetRequest request)
			throws CmisObjectNotFoundException, CmisInvalidArgumentException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "get")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		String streamId = request.getParameter(QueryGetRequest.PARAM_STREAM_ID);
		boolean download = false;
		String downloadParam = request.getParameter(QueryGetRequest.PARAM_DOWNLOAD);
		if (downloadParam != null && downloadParam.length() > 0) {
			String downloadParamLower = downloadParam.trim().toLowerCase(Locale.ENGLISH);
			if ("attachment".equals(downloadParamLower)) {
				download = true;
			} else if ("inline".equals(downloadParamLower)) {
				download = false;
			} else {
				throw new CmisInvalidArgumentException("Invalid download parameter value!");
			}
		}

		BigInteger offset = request.getOffset();
		BigInteger length = request.getLength();
		LOG.info("Method name: {}, getting content stream using this id: {}, repositoryId: {}, offset: {}, length: {}",
				"getContentStream", objectId, request.getRepositoryId(), offset, length);
		ContentStream content = CmisObjectService.Impl.getContentStream(request.getRepositoryId(), objectId, streamId,
				offset, length);
		PostFileResponse fileResponse = new PostFileResponse();
		fileResponse.setDownload(download);
		fileResponse.setOffset(offset);
		fileResponse.setContent(content);
		if (Helpers.isPerfMode()) {
			MetricsInputs.get().getCounter("Count_Downloads_" + request.getObjectId()).inc();
		}
		return fileResponse;
		/*
		 * if (content == null || content.getStream() == null) { throw new
		 * CmisRuntimeException("Content stream is null!"); }
		 * 
		 * String contentType = content.getMimeType(); if (contentType == null) {
		 * contentType = QueryGetRequest.MEDIATYPE_OCTETSTREAM; }
		 * 
		 * String contentFilename = content.getFileName(); if (contentFilename == null)
		 * { contentFilename = "content"; }
		 * 
		 * // send content InputStream in = content.getStream(); OutputStream out =
		 * null; try { out = new FileOutputStream(content.getFileName());
		 * IOUtils.copy(in, out, QueryGetRequest.BUFFER_SIZE); out.flush(); } catch
		 * (Exception e) { LOG.error("writeContent exception: {}, {}", e.getMessage(),
		 * ExceptionUtils.getStackTrace(e)); throw new
		 * IllegalArgumentException("Could not write content: " + e.getMessage(), e); }
		 * finally { IOUtils.closeQuietly(out); IOUtils.closeQuietly(in); } return null;
		 */

	}

	private JSONObject setContent(PostRequest request) throws CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		String changeToken = request.getParameter(QueryGetRequest.PARAM_CHANGE_TOKEN);
		Boolean overwriteFlag = request.getBooleanParameter(QueryGetRequest.PARAM_OVERWRITE_FLAG);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Holder<String> objectIdHolder = new Holder<String>(objectId);
		LOG.info("Method name: {}, setting content stream using this id: {}, repositoryId: {}", "setContentStream",
				objectId, request.getRepositoryId());
		Holder<String> changeTokenHolder = (changeToken == null ? null : new Holder<String>(changeToken));
		if (request.getContentStream() == null) {
			CmisObjectService.Impl.setContentStream(request.getRepositoryId(), objectIdHolder, overwriteFlag,
					changeTokenHolder, null);
		} else {
			CmisObjectService.Impl.setContentStream(request.getRepositoryId(), objectIdHolder, overwriteFlag,
					changeTokenHolder, request.getContentStream());
		}

		String newObjectId = (objectIdHolder.getValue() == null ? objectId : objectIdHolder.getValue());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_DOCUMENT, null);
		if (object == null) {
			MetricsInputs.markUploadErrorMeter();
			throw new CmisRuntimeException("Object is null!");
		}

		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;
	}

	private JSONObject appendContent(PostRequest request) throws CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		String changeToken = request.getParameter(QueryGetRequest.PARAM_CHANGE_TOKEN);
		boolean isLastChunk = request.getBooleanParameter(QueryGetRequest.CONTROL_IS_LAST_CHUNK, false);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();
		Holder<String> objectIdHolder = new Holder<String>(objectId);
		Holder<String> changeTokenHolder = (changeToken == null ? null : new Holder<String>(changeToken));
		LOG.info("Method name: {}, appending content stream using this id: {}, repositoryId: {}, lastChunk: {}",
				"appendContentStream", objectId, request.getRepositoryId(), isLastChunk);
		if (request.getContentStream() == null) {
			CmisObjectService.Impl.appendContentStream(request.getRepositoryId(), objectIdHolder, changeTokenHolder,
					null, isLastChunk);
		} else {
			CmisObjectService.Impl.appendContentStream(request.getRepositoryId(), objectIdHolder, changeTokenHolder,
					request.getContentStream(), isLastChunk);
		}
		String newObjectId = (objectIdHolder.getValue() == null ? objectId : objectIdHolder.getValue());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_DOCUMENT, null);
		if (object == null) {
			throw new CmisRuntimeException("Object is null!");
		}

		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.CHANGE, succinct,
				dateTimeFormat);
		return jsonObject;

	}

	private JSONObject deleteContent(PostRequest request) throws CmisStorageException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		String changeToken = request.getParameter(QueryGetRequest.PARAM_CHANGE_TOKEN);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();

		// execute
		Holder<String> objectIdHolder = new Holder<String>(objectId);
		Holder<String> changeTokenHolder = (changeToken == null ? null : new Holder<String>(changeToken));
		LOG.info("Method name: {}, deleting content stream for this id: {}, repositoryId: {}", "deleteContentStream",
				objectId, request.getRepositoryId());
		CmisObjectService.Impl.deleteContentStream(request.getRepositoryId(), objectIdHolder, changeTokenHolder);
		String newObjectId = (objectIdHolder.getValue() == null ? objectId : objectIdHolder.getValue());
		LOG.info("Method name: {}, getting object using this id: {}, repositoryId: {}", "getObject", newObjectId,
				request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.getSimpleObject(request.getRepositoryId(), newObjectId,
				request.getUserObject(), BaseTypeId.CMIS_DOCUMENT, null);
		if (object == null) {
			throw new CmisRuntimeException("Object is null!");
		}

		// return object
		JSONObject jsonObject = JSONConverter.convert(object, null, JSONConverter.PropertyMode.OBJECT, succinct,
				dateTimeFormat);
		return jsonObject;
	}

	private static JSONObject delete(PostRequest request)
			throws CmisObjectNotFoundException, CmisNotSupportedException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		String[] options = { request.getParameter("typeId") };
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		Boolean allVersions = request.getBooleanParameter(QueryGetRequest.PARAM_ALL_VERSIONS);
		LOG.info("Method name: {}, delete the  object for this id: {}, repositoryId: {}, allVersions: {}",
				"deleteObject", objectId, request.getRepositoryId(), allVersions);
		CmisObjectService.Impl.deleteObject(request.getRepositoryId(), objectId, allVersions, request.getUserObject(),
				options);
		return null;

	}

	private static JSONObject deleteTree(PostRequest request) throws CmisObjectNotFoundException,
			CmisInvalidArgumentException, CmisNotSupportedException, CmisStorageException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		Boolean allVersions = request.getBooleanParameter(QueryGetRequest.PARAM_ALL_VERSIONS);
		UnfileObject unfileObjects = request.getEnumParameter(QueryGetRequest.PARAM_UNFILE_OBJECTS, UnfileObject.class);
		Boolean continueOnFailure = request.getBooleanParameter(QueryGetRequest.PARAM_CONTINUE_ON_FAILURE);
		LOG.info(
				"Method name: {}, deleting the  object tree for this id: {}, repositoryId: {}, allVersions: {}, unfileObjects: {}, continueOnFailure: {}",
				"deleteTree", objectId, request.getRepositoryId(), allVersions, unfileObjects, continueOnFailure);
		FailedToDeleteData ftd = CmisObjectService.Impl.deleteTree(request.getRepositoryId(), objectId, allVersions,
				unfileObjects, continueOnFailure, request.getUserObject());
		if (ftd != null && CmisPropertyConverter.Impl.isNotEmpty(ftd.getIds())) {
			JSONObject JSONObject = JSONConverter.convert(ftd);
			return JSONObject;
		}
		return null;

	}

	private static JSONObject moveObject(PostRequest request)
			throws CmisObjectNotFoundException, CmisNotSupportedException, CmisRuntimeException {
		String permission = request.getUserObject().getPermission();
		if (!Helpers.checkingUserPremission(permission, "post")) {
			throw new CmisRuntimeException(request.getUserName() + " is not authorized to applyAcl.");
		}
		String objectId = request.getObjectId();
		String targetFolderId = request.getParameter(QueryGetRequest.PARAM_TARGET_FOLDER_ID);
		String sourceFolderId = request.getParameter(QueryGetRequest.PARAM_SOURCE_FOLDER_ID);
		boolean succinct = request.getBooleanParameter(QueryGetRequest.CONTROL_SUCCINCT, false);
		DateTimeFormat dateTimeFormat = request.getDateTimeFormatParameter();

		// execute
		Holder<String> objectIdHolder = new Holder<String>(objectId);
		LOG.info(
				"Method name: {}, moving the object using this id: {}, from sourceFolderId: {}, to targetFolderId: {}, repositoryId: {}",
				"moveObject", objectId, sourceFolderId, targetFolderId, request.getRepositoryId());
		ObjectData object = CmisObjectService.Impl.moveObject(request.getRepositoryId(), objectIdHolder, targetFolderId,
				sourceFolderId, null, request.getUserObject());
		JSONObject result = JSONConverter.convert(object, CmisTypeCacheService.get(request.getRepositoryId()),
				JSONConverter.PropertyMode.CHANGE, succinct, dateTimeFormat);

		return result;
	}
}