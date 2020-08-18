package org.sunbird.auth.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;

import java.util.Map;

public class ManagedTokenValidator {
    
    private static ObjectMapper mapper = new ObjectMapper();
    private static LoggerUtil logger = new LoggerUtil(ManagedTokenValidator.class);
    
    /** managedtoken is validated and requestedByUserID values are validated aganist the managedEncToken
     * @param managedEncToken
     * @param requestedByUserId
     * @return
     */
    public static String verify(String managedEncToken, String requestedByUserId) {
        boolean isValid = false;
        String managedFor = JsonKey.UNAUTHORIZED;
        try {
            String[] tokenElements = managedEncToken.split("\\.");
            String header = tokenElements[0];
            String body = tokenElements[1];
            String signature = tokenElements[2];
            String payLoad = header + JsonKey.DOT_SEPARATOR + body;
            Map<Object, Object> headerData = mapper.readValue(new String(decodeFromBase64(header)), Map.class);
            String keyId = headerData.get("kid").toString();
           logger.info(null, "ManagedTokenValidator:verify: keyId: " + keyId);
            Map<String, String> tokenBody = mapper.readValue(new String(decodeFromBase64(body)), Map.class);
            String parentId = tokenBody.get(JsonKey.PARENT_ID);
            String muaId = tokenBody.get(JsonKey.SUB);
           logger.info(null, "ManagedTokenValidator:verify : X-Authenticated-For validation starts.");
            isValid = CryptoUtil.verifyRSASign(payLoad, decodeFromBase64(signature), KeyManager.getPublicKey(keyId).getPublicKey(), JsonKey.SHA_256_WITH_RSA);
           logger.info(null, "ManagedTokenValidator:verify : X-Authenticated-For validation done and isValid = " + isValid);
            isValid &= parentId.equalsIgnoreCase(requestedByUserId);
           logger.info(null, "ManagedTokenValidator:verify : ParentId and RequestedBy userId validation done and isValid = " + isValid);
            if (isValid) {
                managedFor = muaId;
            }
        } catch (Exception ex) {
           logger.error(null, "Exception in ManagedTokenValidator: verify ", ex);
            ex.printStackTrace();
        }
        return managedFor;
    }

    private static byte[] decodeFromBase64(String data) {
        return Base64Util.decode(data, 11);
    }
    
}