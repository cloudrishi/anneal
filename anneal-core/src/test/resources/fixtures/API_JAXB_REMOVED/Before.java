package fixtures.api;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

/**
 * Ground truth fixture — BEFORE migration.
 * Rule: API_JAXB_REMOVED
 * Issue: javax.xml.bind was removed in Java 11.
 */
public class JaxbBefore {

    public void marshal(Object obj) throws JAXBException {
        JAXBContext ctx = JAXBContext.newInstance(obj.getClass());
        Marshaller m = ctx.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }
}
