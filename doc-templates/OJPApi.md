# OJP Endpoint 

## Contact Info

OpenMove team [dev@openmove.com](mailto:dev@openmove.com)


## Documentation

This sandbox allow OTP to operate as a passive node in a OpenJourneyPlanner Cluster. 
This enables an endpoint on `otp/ojp/` (POST Method) in order to allow an active node to query the OTP router via XML.

### Configuration
To enable this functionality, you need to enable the "OJPApi"  feature in the
`otp-config.json` file.

```JSON
//otp-config.json
{
  "OJPApi": true
}
```

### Query

For instance, you can ask for all the stops in the Graph with the following query
```cURL
curl --location --request POST 'http://localhost:8080/otp/ojp' \
--header 'Content-Type: application/xml' \
--data-raw '<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns="http://www.siri.org.uk/siri" version="1.0" xmlns:ojp="http://www.vdv.de/ojp" xsi:schemaLocation="http://www.siri.org.uk/siri ../ojp-xsd-v1.0/OJP.xsd">
  <OJPRequest>
    <ServiceRequest>
      <RequestTimestamp>2024-04-18T07:57:52.438Z</RequestTimestamp>
      <RequestorRef>OJP API Tester</RequestorRef>
      <ojp:OJPLocationInformationRequest>
        <RequestTimestamp>2024-04-18T07:57:52.438Z</RequestTimestamp>
        <ojp:PlaceRef>
           <ojp:LocationName></ojp:LocationName>
        </ojp:PlaceRef>
      </ojp:OJPLocationInformationRequest>
    </ServiceRequest>
  </OJPRequest>
</OJP>'
```

Example Result:

```XML
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<siri:OJP xmlns:ifopt="http://www.ifopt.org.uk/ifopt" xmlns:ojp="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" xmlns:acsb="http://www.ifopt.org.uk/acsb">
    <siri:OJPResponse>
        <siri:ServiceDelivery>
            <siri:ResponseTimestamp>2024-04-18T15:40:58+02:00</siri:ResponseTimestamp>
            <siri:ProducerRef>OpenTripPlanner</siri:ProducerRef>
            <siri:Status>true</siri:Status>
            <ojp:OJPLocationInformationDelivery>
                <siri:Status>true</siri:Status>
                <ojp:CalcTime>29</ojp:CalcTime>
                <ojp:Location>
                    <ojp:Location>
                        <ojp:StopPoint>
                            <siri:StopPointRef>sta:it:22021:1635:0:1940</siri:StopPointRef>
                            <ojp:StopPointName>
                                <ojp:Text xml:lang="IT">Bolzano, Parco Firmian</ojp:Text>
                            </ojp:StopPointName>
                            <ojp:WheelchairAccessible>false</ojp:WheelchairAccessible>
                        </ojp:StopPoint>
                        <ojp:LocationName>
                            <ojp:Text xml:lang="IT">Bolzano, Parco Firmian</ojp:Text>
                        </ojp:LocationName>
                        <ojp:GeoPosition>
                            <siri:Longitude>11.3099406</siri:Longitude>
                            <siri:Latitude>46.4934325</siri:Latitude>
                        </ojp:GeoPosition>
                    </ojp:Location>
                    <ojp:Complete>true</ojp:Complete>
                    <ojp:Probability>2.0E-4</ojp:Probability>
                </ojp:Location>
            </ojp:OJPLocationInformationDelivery>
        </siri:ServiceDelivery>
    </siri:OJPResponse>
</siri:OJP>
```

## Changelog

- Create initial sandbox implementation (April 2024)
