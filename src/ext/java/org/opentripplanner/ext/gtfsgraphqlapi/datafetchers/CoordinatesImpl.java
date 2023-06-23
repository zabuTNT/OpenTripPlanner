package org.opentripplanner.ext.gtfsgraphqlapi.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.ext.gtfsgraphqlapi.generated.GraphQLDataFetchers;

public class CoordinatesImpl implements GraphQLDataFetchers.GraphQLCoordinates {

  @Override
  public DataFetcher<Double> lat() {
    return environment -> getSource(environment).getY();
  }

  @Override
  public DataFetcher<Double> lon() {
    return environment -> getSource(environment).getX();
  }

  private Coordinate getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
