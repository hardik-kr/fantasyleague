package com.cricket.fantasyleague.util;

public final class AppConstants 
{
    public final class user
    {
        public final static String USER_NOT_FOUND = "User not found : %s" ;
        public final static String INVALID_CREDENTIAL = "Invalid credential !! username or password" ;
        public final static String USER_CREATED_SUCCESS = "User created successfully" ;
        public static final String ALREADY_EXIST = "User already exist with %s : %s";
    } 

    public final class jwt
    {
        public final static String ACCESS_DENIED = "Access Denied !!" ;
        public final static String INVALID_JWT = "Invalid JWT token or token expired !!" ;
        public final static String JWT_INVALID_USERNAME = "Illegal Argument while fetching the username !!" ;
        public final static String JWT_INVALID_TOKEN = "Some changed has done in token !! Invalid Token" ;
        public final static String JWT_EXPIRED_TOKEN = "Given jwt token is expired !!" ;
        public final static String JWT_INVALID_HEADER = "Invalid Header Value !! " ; 
        public final static String JWT_VERIFICATION_FAILS = "Token verification fails !!" ;
        public final static String JWT_SOMETHING_WENT_WRONG = "Something went wrong !!\nMsg : %s";
    }

    public final class URI
    {
        public final static String SCORECARD_FULL = "http://localhost:9090/scorecard/full/" ;
    }

    public final class error
    {
        public static final String MATCH_NOT_FOUND = "Match Not found with given ID : %s" ;
        public static final String RUNTIME_ERROR = "Runtime Error" ;
        public static final String TEAM_NOT_FOUND = "Team not found : %s or %s" ;
        public static final String EXCEPTION = "Error !! " ; 
        public static final String DATABASE_ERROR = "%s data not saved to DB, %s" ;
        public static final String PLAYER_NOT_FOUND = "Player '%s' not found" ;
        public static final String PARSING_PLAYER = "while extracting player info in %s, %s" ;
        public static final String PARSING_SCORE = "while extracting Inngs Score in %s, %s" ;
        public static final String PARSING_COMMENTARY = "while extracting commentary /nBallnbr : %s\nCommentaryText : %s\nMsg = %s" ;
        public static final String COMM_LIST_NULL = "Commentary List is NULL" ;
        public static final String UNKNOWN_HOST = "Unable to connect to HOST server : %s" ;
    }

    public final class entity
    {
        public static final String PLAYERPOINTS = "PLAYER POINTS" ;
        public static final String USERPOINTS = "USER POINTS" ;
        public static final String USER = "USER" ;
        public static final String MATCH = "MATCHES" ;
        public static final String PLAYER = "PLAYER" ;
        public static final String TEAMS = "TEAMS" ;
        public static final String USERTRANSFER = "USER TRANSFER" ;
        public static final String USEROVERALLPOINTS = "USER OVERALL POINTS" ;
        public static final String USERMATCHSTATSDRAFT = "USER MATCH STATS DRAFT" ;
    }

    public final class FantasyPoints
    {
        public static final Integer TOTAL_TRANSFER = 120 ;
        public static final Integer TOTAL_BOOSTER = 7 ;
    }

    public final class masterdata
    {
        public final static String MATCH_SUCCESS = "All matches data saved for id : %s" ;
        public final static String PLAYER_SUCCESS = "All player data saved for id : %s" ;
    }

    public final class URL
    {
        public static final String HOST = "https://www.cricbuzz.com";
        public static final String COMMENTARY = "/api/cricket-match/commentary/";
        public static final String SCORECARD = "/api/html/cricket-scorecard/";
        public static final String SQUAD = "/cricket-series/squads/";
        public static final String MATCH = "/cricket-series/" ;
    }  
    
}
