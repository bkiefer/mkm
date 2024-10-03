package de.dfki.drz.mkm;

import de.dfki.mlt.drz.eurocommand_api.ApiClient;
import de.dfki.mlt.drz.eurocommand_api.ApiException;
import de.dfki.mlt.drz.eurocommand_api.Configuration;
import de.dfki.mlt.drz.eurocommand_api.api.ExternalAuthenticationApi;
import de.dfki.mlt.drz.eurocommand_api.api.HealthCheckApi;
import de.dfki.mlt.drz.eurocommand_api.api.MessageApi;
import de.dfki.mlt.drz.eurocommand_api.api.MissionApi;
import de.dfki.mlt.drz.eurocommand_api.api.MissionResourceApi;
import de.dfki.mlt.drz.eurocommand_api.api.ResourceApi;
import de.dfki.mlt.drz.eurocommand_api.model.BearerAuthenticateRestApiContract;
import de.dfki.mlt.drz.eurocommand_api.model.CimgateStatusEnum;
import de.dfki.mlt.drz.eurocommand_api.model.CimgateStatusRestApiContract;
import de.dfki.mlt.drz.eurocommand_api.model.MessageRestApiContract;
import de.dfki.mlt.drz.eurocommand_api.model.MissionCategoryEnum;
import de.dfki.mlt.drz.eurocommand_api.model.MissionResourceRestApiContract;
import de.dfki.mlt.drz.eurocommand_api.model.MissionRestApiContract;
import de.dfki.mlt.drz.eurocommand_api.model.ProdicoBridgeUserDTO;
import de.dfki.mlt.drz.eurocommand_api.model.ResourceRestApiContract;
import de.dfki.mlt.drz.eurocommand_api.model.RestApiMessageStatusEnum;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Wrapper around OpenAPI client. */
public class EuroCommandClient {

  // provided credentials
  private static final String USER = "PRODICO.Bridge";
  private static final String PASSWORD = "zufa@u7pt7$po2o$v9cg9bc";
  private static final String CX5_SECURITY_TOKEN = "ry$lk6$nqgm$r356!qv$1qk8b$6$du3!z$yx9u8";

  static final String BASE_URL = "https://cimgate.eurocommand.com:444";

  // number of minutes after bearer token becomes invalid
  private static final long BEARER_TOKEN_TTL = 15;

  private LocalDateTime bearerTokenValidUntil;

  // all relevant APIs
  private ApiClient apiClient;
  private ExternalAuthenticationApi authApi;
  private HealthCheckApi healthApi;
  private ResourceApi resourceApi;
  private MissionApi missionApi;
  private MissionResourceApi missionResourceApi;
  private MessageApi messageApi;

  /** Create new client. */
  public EuroCommandClient() {
    this.apiClient = Configuration.getDefaultApiClient();
    this.apiClient.setApiKey(CX5_SECURITY_TOKEN);
    this.authApi = new ExternalAuthenticationApi(this.apiClient);
    this.authApi.setCustomBaseUrl(BASE_URL);
    this.healthApi = new HealthCheckApi();
    this.healthApi.setCustomBaseUrl(BASE_URL);
    this.resourceApi = new ResourceApi(this.apiClient);
    this.resourceApi.setCustomBaseUrl(BASE_URL);
    this.missionApi = new MissionApi(this.apiClient);
    this.missionApi.setCustomBaseUrl(BASE_URL);
    this.missionResourceApi = new MissionResourceApi(this.apiClient);
    this.missionResourceApi.setCustomBaseUrl(BASE_URL);
    this.messageApi = new MessageApi(this.apiClient);
    this.messageApi.setCustomBaseUrl(BASE_URL);
  }

  private void renewBearerToken() throws ApiException {
    if (this.bearerTokenValidUntil != null
        && this.bearerTokenValidUntil.isAfter(LocalDateTime.now())) {
      // bearer token still valid, nothing to do
      System.out.println("bearer token still valid");
      return;
    }

    ProdicoBridgeUserDTO prodicoBridgeUserDTO = new ProdicoBridgeUserDTO();
    prodicoBridgeUserDTO.setUserName(USER);
    prodicoBridgeUserDTO.setPassword(PASSWORD);

    BearerAuthenticateRestApiContract authRes =
        this.authApi.apiV1AuthenticatePost(prodicoBridgeUserDTO);
    String bearerToken = authRes.getResult();
    System.out.println("bearer token renewed: " + bearerToken);
    // add bearer token api client so that it is used in all subsequent calls
    this.apiClient.setBearerToken(bearerToken);
    this.bearerTokenValidUntil = LocalDateTime.now().plusMinutes(BEARER_TOKEN_TTL).minusMinutes(1);
  }

  /**
   * Check if server is alive.
   *
   * @return {@code true} if alive
   * @throws ApiException
   */
  public boolean isHealthy() throws ApiException {
    CimgateStatusRestApiContract health = this.healthApi.apiV1HealthCheckGet();
    return health.getCimgateStatusEnum() == CimgateStatusEnum.NUMBER_1;
  }

  /**
   * Get resources.
   *
   * @param radioCall optional, use {@code null} for none
   * @return list of resources
   * @throws ApiException
   */
  public List<ResourceRestApiContract> getResources(String radioCall) throws ApiException {
    renewBearerToken();
    return this.resourceApi.internalApiV1RestApiResourceGet(radioCall);
  }

  /**
   * Get missions.
   *
   * @return list of missions
   * @throws ApiException
   */
  public List<MissionRestApiContract> getMissions() throws ApiException {

    // this takes multiple optional parameters, add to method parameters when required
    MissionCategoryEnum missionCategory = null;
    Boolean isDisabled = null;
    Boolean isLocked = null;
    Boolean eLS = null;
    Boolean isTakenOver = null;
    Boolean hasEndDate = null;
    OffsetDateTime startDateMin = null;
    OffsetDateTime dateModifiedAfter = null;
    UUID mainDepartmentStationId = null;
    Boolean includeLeadingResourceId = null;

    renewBearerToken();
    return this.missionApi.internalApiV1RestApiMissionGet(
        missionCategory,
        isDisabled,
        isLocked,
        eLS,
        isTakenOver,
        hasEndDate,
        startDateMin,
        dateModifiedAfter,
        mainDepartmentStationId,
        includeLeadingResourceId);
  }

  /**
   * Get all mission resources for given mission id.
   *
   * @param missionId mission id
   * @return list of mission resources
   * @throws ApiException
   */
  public List<MissionResourceRestApiContract> getMissionResources(UUID missionId)
      throws ApiException {
    // requires an optional external mission id, add to method parameters when required
    String missionExternalId = null;
    renewBearerToken();
    return this.missionResourceApi.internalApiV1RestApiMissionMissionIdMissionResourceGet(
        missionId, missionExternalId);
  }

  /**
   * Send message to mission with given mission id.
   *
   * @param text message text
   * @param senderName message sender
   * @param receiverName message receiver
   * @param missionId mission id
   * @throws ApiException
   */
  public void sendMessage(String text, String senderName, String receiverName, UUID missionId)
      throws ApiException {

    // build message
    MessageRestApiContract message =
        new MessageRestApiContract()
            .date(OffsetDateTime.now().toString())
            .text(text)
            .messageStatus(RestApiMessageStatusEnum.NUMBER_1)
            .senderName(senderName)
            .receiverName(receiverName);

    // requires an optional external mission id, add to method parameters when required
    String missionExternalId = null;

    renewBearerToken();
    this.messageApi.internalApiV1RestApiMissionMissionIdMessagePost(
        missionId, missionExternalId, message);
  }

  /**
   * Get all messages for mission with given mission id
   *
   * @param missionId mission id
   * @return list of messages
   * @throws ApiException
   */
  public List<MessageRestApiContract> getMesages(UUID missionId) throws ApiException {
    // requires an optional external mission id, add to method parameters when required
    String missionExternalId = null;
    renewBearerToken();
    return this.messageApi.internalApiV1RestApiMissionMissionIdMessageGet(
        missionId, missionExternalId);
  }
}
