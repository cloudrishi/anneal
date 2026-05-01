package fixtures.api;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

/**
 * Ground truth fixture — AFTER migration.
 * Rule: API_JAXB_REMOVED
 * Fix: Replace javax.xml.bind with jakarta.xml.bind.
 * AutoApplicable: true — import replacement is deterministic.
 * Note: requires dependency jakarta.xml.bind:jakarta.xml.bind-api:4.0.0 in build file.
 */
public class JaxbAfter {

    public void marshal(Object obj) throws JAXBException {
        JAXBContext ctx = JAXBContext.newInstance(obj.getClass());
        Marshaller m = ctx.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }
}
