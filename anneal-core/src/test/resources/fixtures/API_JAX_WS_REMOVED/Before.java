package fixtures.api;

import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import java.net.URL;

/**
 * Ground truth fixture — BEFORE migration.
 * Rule: API_JAX_WS_REMOVED
 * Issue: javax.xml.ws was removed in Java 11.
 */
public class JaxWsBefore {

    public Service createService(URL wsdlUrl, javax.xml.namespace.QName name)
            throws WebServiceException {
        return Service.create(wsdlUrl, name);
    }
}
