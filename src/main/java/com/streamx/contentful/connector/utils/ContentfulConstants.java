package com.streamx.contentful.connector.utils;

public class ContentfulConstants {
  public static final String FIELD_ID = "id";
  public static final String FIELD_SYS = "sys";
  public static final String FIELD_TYPE = "type";
  public static final String FIELD_LINK_TYPE = "linkType";

  public static final String FIELD_VALUE_LINK = "Link";
  public static final String FIELD_VALUE_ASSET = "Asset";
  public static final String FIELD_VALUE_ENTRY = "Entry";

  public static final String QUERY_PARAM_SYS_ID = FIELD_SYS + "." + FIELD_ID;
  public static final String QUERY_PARAM_INCLUDE_LEVEL = "include";
  public static final String QUERY_PARAM_SYS_ID_JOINED = QUERY_PARAM_SYS_ID + "[in]";


}
