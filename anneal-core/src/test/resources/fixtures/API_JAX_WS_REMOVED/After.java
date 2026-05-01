package fixtures.api;

import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import java.net.URL;

/**
 * Ground truth fixture — AFTER migration.
 * Rule: API_JAX_WS_REMOVED
 * Fix: Replace javax.xml.ws with jakarta.xml.ws.
 * AutoApplicable: true — import replacement is deterministic.
 * Note: requires dependency jakarta.xml.ws:jakarta.xml.ws-api:4.0.0 in build file.
 */
public class JaxWsAfter {

    public Service createService(URL wsdlUrl, javax.xml.namespace.QName name)
            throws WebServiceException {
        return Service.create(wsdlUrl, name);
    }
}
