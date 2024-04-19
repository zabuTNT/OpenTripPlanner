package org.opentripplanner.ext.ojp;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

import org.opentripplanner.transit.service.TransitService;

import de.vdv.ojp.model.AbstractFunctionalServiceRequestStructure;
import de.vdv.ojp.model.OJP;
import de.vdv.ojp.model.OJPResponseStructure;
import de.vdv.ojp.model.ParticipantRefStructure;
import de.vdv.ojp.model.ServiceDelivery;

import de.vdv.ojp.OJPExchangePointsDeliveryStructure;
import de.vdv.ojp.OJPExchangePointsRequestStructure;
import de.vdv.ojp.OJPLocationInformationDeliveryStructure;
import de.vdv.ojp.OJPLocationInformationRequestStructure;
import de.vdv.ojp.OJPMultiPointTripDeliveryStructure;
import de.vdv.ojp.OJPMultiPointTripRequestStructure;
import de.vdv.ojp.OJPStopEventDeliveryStructure;
import de.vdv.ojp.OJPStopEventRequestStructure;
import de.vdv.ojp.OJPTripDeliveryStructure;
import de.vdv.ojp.OJPTripInfoDeliveryStructure;
import de.vdv.ojp.OJPTripInfoRequestStructure;
import de.vdv.ojp.OJPTripRequestStructure;
import de.vdv.ojp.ObjectFactory;


import java.time.ZonedDateTime;
import java.util.List;

/**
 * OTP simple built-in OJP API module.
 */
@Path("/ojp")
@Produces(MediaType.APPLICATION_XML)
public class OJPResource {

		private final OtpServerRequestContext serverContext;
    private final ObjectFactory factory = new ObjectFactory();

		private TransitService transitService() {
			return serverContext.transitService();
		}
    public OJPResource (@Context OtpServerRequestContext otpServer, @PathParam("ignoreRouterId") String ignoreRouterId) {
			this.serverContext = otpServer;
    }

    /**
     * OJP using OTP graph
     *
     * @return OJP response
     */
    @POST
    @Consumes("application/xml")
    public Response ojpExec (String requestRaw) {
			OJP request = null;
			try {
				StringReader sr = new StringReader(requestRaw);
				JAXBContext jaxbContext = JAXBContext.newInstance(OJP.class);
				Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
				request = (OJP) unmarshaller.unmarshal(sr);
			}catch (Exception e){
				return Response
					.status(Response.Status.BAD_REQUEST)
					.type(MediaType.TEXT_PLAIN_TYPE)
					.entity("Error Parsing XML")
					.build();
			}

    	List<JAXBElement<? extends AbstractFunctionalServiceRequestStructure>> list = request.getOJPRequest().getServiceRequest().getAbstractFunctionalServiceRequest();
    	
    	OJP ojp = new OJP();
    	OJPResponseStructure v = new OJPResponseStructure();
    	ServiceDelivery s = new ServiceDelivery();
    	
    	ParticipantRefStructure producer = new ParticipantRefStructure();
    	producer.setValue("OpenTripPlanner");
			s.setProducerRef(producer );
		
    	
    	for(JAXBElement<? extends AbstractFunctionalServiceRequestStructure> elem : list) {
    		if(elem.getDeclaredType().equals(OJPLocationInformationRequestStructure.class)) {

    			OJPLocationInformationRequestStructure informationRequest = (OJPLocationInformationRequestStructure) elem.getValue();
    			OJPLocationDeliveryFactory locationDeliveryFactory = new OJPLocationDeliveryFactory(serverContext, informationRequest, factory);
    			OJPLocationInformationDeliveryStructure location = locationDeliveryFactory.create();
    			s.getAbstractFunctionalServiceDelivery().add(factory.createOJPLocationInformationDelivery(location));
    			
    		}
    		
    		if(elem.getDeclaredType().equals(OJPStopEventRequestStructure.class)) {
    			OJPStopEventRequestStructure stopEventRequest = (OJPStopEventRequestStructure) elem.getValue();
    			OJPStopEventsFactory eventDeliveryFactory = new OJPStopEventsFactory(serverContext,stopEventRequest, factory);
    			OJPStopEventDeliveryStructure event = eventDeliveryFactory.create();
					s.getAbstractFunctionalServiceDelivery().add(factory.createOJPStopEventDelivery(event));
    		}
    		
    		if(elem.getDeclaredType().equals(OJPTripInfoRequestStructure.class)) {
    			OJPTripInfoRequestStructure tripInfoRequest = (OJPTripInfoRequestStructure) elem.getValue();
    			OJPTripInfoFactory tripInfoFactory = new OJPTripInfoFactory(serverContext,tripInfoRequest, factory);
    			OJPTripInfoDeliveryStructure tripInfo = tripInfoFactory.create();
				s.getAbstractFunctionalServiceDelivery().add(factory.createOJPTripInfoDelivery(tripInfo));
    		}
    		
    		if(elem.getDeclaredType().equals(OJPTripRequestStructure.class)) {
    			OJPTripRequestStructure tripRequest = (OJPTripRequestStructure) elem.getValue();
    			
    			OJPTripFactory tripFactory = new OJPTripFactory(serverContext,tripRequest,factory);
    			OJPTripDeliveryStructure trip = tripFactory.create();
    			
    			s.getAbstractFunctionalServiceDelivery().add(factory.createOJPTripDelivery(trip));
    		}
    		
    		if(elem.getDeclaredType().equals(OJPExchangePointsRequestStructure.class)) {
    			OJPExchangePointsRequestStructure exchangePointRequest = (OJPExchangePointsRequestStructure) elem.getValue();
    			
    			OJPExchangePointFactory exchangePointFactory = new OJPExchangePointFactory(serverContext,exchangePointRequest,factory);
    			OJPExchangePointsDeliveryStructure exchange = exchangePointFactory.create();
    			
    			s.getAbstractFunctionalServiceDelivery().add(factory.createOJPExchangePointsDelivery(exchange));
    		}
    		
    		if(elem.getDeclaredType().equals(OJPMultiPointTripRequestStructure.class)) {
    			OJPMultiPointTripRequestStructure tripMultiRequest = (OJPMultiPointTripRequestStructure) elem.getValue();
    			OJPMultiPointTripFactory multiTripFactory = new OJPMultiPointTripFactory(serverContext,tripMultiRequest,factory);
    			OJPMultiPointTripDeliveryStructure multiTrip = multiTripFactory.create();
    			
				s.getAbstractFunctionalServiceDelivery().add(factory.createOJPMultiPointTripDelivery(multiTrip ));
    			
    		}
    	}
    	
  
    	ZonedDateTime responseTimestamp = ZonedDateTime.now();
    	s.setResponseTimestamp(responseTimestamp);
		s.setStatus(true);
    	v.setServiceDelivery(s);
    	
    	ojp.setOJPResponse(v);
    	System.out.println("END!");
			System.out.println(ojp.toString());

			try {
				StringWriter writer = new StringWriter();
				JAXBContext jaxbContext = JAXBContext.newInstance(OJP.class);
				Marshaller marshaller = jaxbContext.createMarshaller();
				marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
				marshaller.marshal(ojp, writer);

				String resultXml = writer.toString();

				return Response.status(Response.Status.OK)
					.type(MediaType.APPLICATION_XML)
					.entity(resultXml)
					.build();
			}catch (Exception e){
				e.printStackTrace();
				return Response
					.status(Response.Status.BAD_REQUEST)
					.type(MediaType.TEXT_PLAIN_TYPE)
					.entity("Error Marshalling XML")
					.build();
			}


    }

}
