package com.cricket.fantasyleague.util;

public final class FantasyPointSystem 
{
    public final class Batting
    {
        public final static Integer RUN = 1 ;
        public final static Integer BOUNDARY = 1 ;
        public final static Integer SIX = 2 ;
        public final static Integer HALF_CENTURY = 4 ;
        public final static Integer CENTURY = 8 ;
        public final static Integer DISMISSAL_DUCK = -3 ;
    }

    public final class myBatting
    {
        public final static Integer RUN = 1 ;
        public final static Integer BOUNDARY = 1 ;
        public final static Integer SIX = 2 ;
        public final static Integer THIRTY = 5 ;
        public final static Integer HALF_CENTURY = 10 ;
        public final static Integer CENTURY = 20 ;
        public final static Integer DISMISSAL_DUCK = -5 ;
    }

    public final class Bowling
    {
        public final static Integer WICKET = 25 ;
        public final static Integer LBW_BOWLED = 8 ;
        public final static Integer FOUR_WICKET = 4 ;
        public final static Integer FIVE_WICKET = 8 ;
        public final static Integer MAIDEN = 4 ;
    }

    public final class myBowling
    {
        public final static Integer WICKET = 20 ;
        public final static Integer LBW_BOWLED = 5 ;
        public final static Integer WICKET_BONUS = 5 ;
        public final static Integer MAIDEN = 5 ;
    }

    public final class Fielding
    {
        public final static Integer CATCH = 8 ;
        public final static Integer CATCH_BONUS = 4 ;
        public final static Integer STUMPING = 12 ;
        public final static Integer RUN_OUT_DIRECT = 12 ;
        public final static Integer RUN_OUT = 6 ;
    }

    public final class myFielding
    {
        public final static Integer CATCH = 5 ;
        public final static Integer CATCH_BONUS = 5 ;
        public final static Integer STUMPING = 10 ;
        public final static Integer STUMPING_BONUS = 5 ;
        public final static Integer RUN_OUT_DIRECT = 10 ;
        public final static Integer RUN_OUT = 5 ;
    }

    public final class others
    {
        public final static Integer CAPTAIN = 2 ;
        public final static Double VICE_CAPTAIN = 1.5 ;
        public final static Double IN_PLAYING11 = 5.0 ;
        public final static Integer STRIKERATE_MAX = 20 ;
        public final static Integer STRIKERATE_MIN = -10 ;
        public final static Double ECONOMY_MAX = 20.0 ;
        public final static Double ECONOMY_MIN = -10.0 ;
    }  
    
    public final class strikerate
    {
        public final static Integer ABOVE_14 = 6 ;
        public final static Integer BETWEEN_12_14 = 4 ;
        public final static Integer BETWEEN_10_12 = 2 ;
        public final static Integer BETWEEN_4_5 = -2 ;
        public final static Integer BETWEEN_3_399 = -4 ;
        public final static Integer BELOW_3 = -6 ;
    }

    public final class mystrikerate
    {
        public final static Integer ABOVE_150 = 6 ;
        public final static Integer BETWEEN_130_150 = 4 ;
        public final static Integer BETWEEN_120_130 = 2 ;
        public final static Integer BETWEEN_60_80 = -2 ;
        public final static Integer BETWEEN_50_60 = -4 ;
        public final static Integer BELOW_50 = -6 ;
    }

    public final class economy
    {
        public final static Integer BELOW_25 = 6 ;
        public final static Integer BETWEEN_25_349 = 4 ;
        public final static Integer BETWEEN_35_45 = 2 ;
        public final static Integer BETWEEN_7_8 = -2 ;
        public final static Integer BETWEEN_801_9 = -4 ;
        public final static Integer ABOVE_9 = -6 ;
    }
}
