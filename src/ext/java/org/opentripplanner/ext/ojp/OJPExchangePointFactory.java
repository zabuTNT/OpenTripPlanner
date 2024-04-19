package org.opentripplanner.ext.ojp;

import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.service.TransitService;

import de.vdv.ojp.model.BusSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.ErrorDescriptionStructure;
import de.vdv.ojp.model.FunicularSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.LocationStructure;
import de.vdv.ojp.model.MetroSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.NaturalLanguageStringStructure;
import de.vdv.ojp.model.RailSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.ServiceDeliveryErrorConditionStructure;
import de.vdv.ojp.model.TelecabinSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.TramSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.VehicleModesOfTransportEnumeration;
import de.vdv.ojp.model.WaterSubmodesOfTransportEnumeration;

import de.vdv.ojp.ExchangePointsResultStructure;
import de.vdv.ojp.InternationalTextStructure;
import de.vdv.ojp.ModeStructure;
import de.vdv.ojp.OJPExchangePointsDeliveryStructure;
import de.vdv.ojp.OJPExchangePointsRequestStructure;
import de.vdv.ojp.ObjectFactory;
import de.vdv.ojp.PlaceStructure;
import de.vdv.ojp.StopPlaceRefStructure;
import de.vdv.ojp.StopPlaceStructure;

public class OJPExchangePointFactory {
	
	private final OJPExchangePointsRequestStructure exchangePointsRequest;
	private final OtpServerRequestContext serverRequestContext;
	private List<VehicleModesOfTransportEnumeration> modeList = new ArrayList<>();
	private List<String> requestModes = new ArrayList<>();
	private ObjectFactory factory;
	
	List<String> allOTPModes = Arrays.asList("TRAM","SUBWAY","RAIL","BUS","FERRY","GONDOLA","FUNICULAR");
	
	public OJPExchangePointFactory(OtpServerRequestContext serverRequestContext, OJPExchangePointsRequestStructure exchangePointsRequest, ObjectFactory factory) {
		this.serverRequestContext = serverRequestContext;
		this.exchangePointsRequest = exchangePointsRequest;
		this.factory = factory;
	}

	public OJPExchangePointsDeliveryStructure create() {
		OJPExchangePointsDeliveryStructure location = new OJPExchangePointsDeliveryStructure();
		
		long timeStart = System.currentTimeMillis();
		
		String stopCode = null;
		
		int continueAt = 0;
		boolean includePtModes = false;
		long maxResults = Integer.MAX_VALUE;
		
		if(exchangePointsRequest.getPlaceRef() != null) {
			if(exchangePointsRequest.getPlaceRef().getStopPointRef() != null) {
				stopCode = exchangePointsRequest.getPlaceRef().getStopPointRef().getValue();
			} else if(exchangePointsRequest.getPlaceRef().getStopPlaceRef() != null) {
				stopCode = exchangePointsRequest.getPlaceRef().getStopPlaceRef().getValue();
			}	
		}
		
		

		if(exchangePointsRequest.getParams() != null) {
						
			
			if(exchangePointsRequest.getParams().getContinueAt()!=null) {
				continueAt = exchangePointsRequest.getParams().getContinueAt().intValue();
			}
			
			if(exchangePointsRequest.getParams().getNumberOfResults() != null) {
				maxResults = exchangePointsRequest.getParams().getNumberOfResults().longValue();
			}
			
			if(exchangePointsRequest.getParams().getPtModes() != null) {
				modeList = exchangePointsRequest.getParams().getPtModes().getPtMode();
				boolean excludeModes = exchangePointsRequest.getParams().getPtModes().isExclude();
				for(VehicleModesOfTransportEnumeration mode : modeList) {
					String otpMode = CommonOJP.convertOTPModes(mode);
					if(otpMode != null) {
						if(excludeModes == false) {
							requestModes.add(otpMode);
						}else {
							allOTPModes.remove(otpMode);
						}
					}
					
				}
				if(excludeModes == true) {
					requestModes.addAll(allOTPModes);
				}
			}else {
				requestModes.addAll(allOTPModes);
			}
			
			
			
		}
		
		List<Station> stations = new ArrayList<>();
		
		if(stopCode != null) {
			//search by id
			try {
				stations.add(station(stopCode));
			}catch (NotFoundException e){
				//TODO ?
				e.printStackTrace();
			}
		}else {
			stations = transitService().getStations().stream().toList();
		}


				
		List<Station> stops = stations.stream().filter(station -> {
			//filter by modes
			if(!requestModes.isEmpty()) {

				List<Route> routes = station.getChildStops().stream()
					.flatMap(c -> transitService().getRoutesForStop(c).stream())
					.distinct().toList();

				List<VehicleModesOfTransportEnumeration> types = CommonOJP.getTraverseModes(new HashSet<>(routes));
						
				if(Collections.disjoint(requestModes, types)) { //at list one mode must be in common
					return false;
				}
			}
						
			return true;
		}).skip(continueAt).limit(maxResults).toList();
		
		BigInteger mContinueAt = BigInteger.valueOf(continueAt + stops.size());
		
		if(stops.isEmpty()) {
			location.setStatus(false);
			ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
			ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
			descr.setValue("No Exchange Point");
			location.setErrorCondition(error.withDescription(descr ));
			long timeEnd = System.currentTimeMillis();
			location.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
			return location;			
		}else {
			location.setStatus(true);
			if(stops.size() == maxResults) {
				location.setContinueAt(mContinueAt);
			}
		}
		
		
		
		
		for(Station stop : stops) {

			Collection<Agency> agencies = transitService()
				.getAgencies()
				.stream()
				.filter(agency -> agency.getId().getFeedId().equals(stop.getId().getFeedId()))
				.toList();

			String lang = agencies.iterator().next().getLang();
      assert lang != null;
      Locale locale = new Locale(lang);


			PlaceStructure p = new PlaceStructure();
			StopPlaceStructure sp = new StopPlaceStructure();
			InternationalTextStructure it = new InternationalTextStructure();
			NaturalLanguageStringStructure nL = new NaturalLanguageStringStructure();
			nL.setLang(lang);
			nL.setValue(stop.getName().toString(locale));
			it.setText(nL);
			sp.setStopPlaceName(it);
			//sp.setWheelchairAccessible(stop == 1);
			LocationStructure geo = new LocationStructure();
			geo.setLatitude(BigDecimal.valueOf(stop.getLat()));
			geo.setLongitude(BigDecimal.valueOf(stop.getLon()));
			p.setGeoPosition(geo);
			StopPlaceRefStructure sRef = new StopPlaceRefStructure();
			sRef.setValue(stop.getCode());
			sp.setStopPlaceRef(sRef );
			
			p.setStopPlace(sp);
			p.setLocationName(it);
			
			
			ExchangePointsResultStructure exchangePoint = new ExchangePointsResultStructure();
			exchangePoint.setPlace(p);

			List<Route> routes = stop.getChildStops().stream()
				.flatMap(c -> transitService().getRoutesForStop(c).stream())
				.distinct().toList();
			
			routes.stream().map(r -> r.getGtfsType()).distinct().forEach(type -> {
				ModeStructure m = new ModeStructure();
				switch(type) {
					case 0: m.setPtMode(VehicleModesOfTransportEnumeration.TRAM); m.setTramSubmode(TramSubmodesOfTransportEnumeration.ALL_TRAM_SERVICES); break;
					case 1: m.setPtMode(VehicleModesOfTransportEnumeration.METRO); m.setMetroSubmode(MetroSubmodesOfTransportEnumeration.ALL_RAIL_SERVICES); break;
					case 2: m.setPtMode(VehicleModesOfTransportEnumeration.RAIL); m.setRailSubmode(RailSubmodesOfTransportEnumeration.ALL_RAIL_SERVICES); break;
					case 3: m.setPtMode(VehicleModesOfTransportEnumeration.BUS); m.setBusSubmode(BusSubmodesOfTransportEnumeration.ALL_BUS_SERVICES); break;
					case 4: m.setPtMode(VehicleModesOfTransportEnumeration.FERRY_SERVICE); m.setWaterSubmode(WaterSubmodesOfTransportEnumeration.ALL_WATER_TRANSPORT_SERVICES); break;
					case 5: m.setPtMode(VehicleModesOfTransportEnumeration.TRAM); m.setTramSubmode(TramSubmodesOfTransportEnumeration.CITY_TRAM); break;
					case 6: m.setPtMode(VehicleModesOfTransportEnumeration.TELECABIN); m.setTelecabinSubmode(TelecabinSubmodesOfTransportEnumeration.ALL_TELECABIN_SERVICES); break;
					case 7: m.setPtMode(VehicleModesOfTransportEnumeration.FUNICULAR); m.setFunicularSubmode(FunicularSubmodesOfTransportEnumeration.ALL_FUNICULAR_SERVICES); break;
					default: m.setPtMode(VehicleModesOfTransportEnumeration.UNKNOWN); break;
				}
				exchangePoint.getMode().add(m);
			});
			
			location.getPlace().add(exchangePoint );
		}
		long timeEnd = System.currentTimeMillis();
		location.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
		return location;
	}

	private TransitService transitService() {
		return this.serverRequestContext.transitService();
	}

	private Station station(String stationId) {
		var station = transitService().getStationById(CommonOJP.createId("stationId", stationId));
		return CommonOJP.validateExist("Station", station, "stationId", stationId);
	}

	
}
