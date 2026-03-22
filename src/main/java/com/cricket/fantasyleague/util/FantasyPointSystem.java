package com.cricket.fantasyleague.util;

public final class FantasyPointSystem 
{
    public final class Batting
    {
        public final static Integer RUN = 1 ;
        public final static Integer BOUNDARY = 1 ;
        public final static Integer SIX = 2 ;
        public final static Integer THIRTY_BONUS = 4 ;
        public final static Integer HALF_CENTURY = 8 ;
        public final static Integer CENTURY = 16 ;
        public final static Integer DISMISSAL_DUCK = -2 ;
    }

    public final class Bowling
    {
        public final static Integer WICKET = 25 ;
        public final static Integer MAIDEN = 12 ;
        public final static Integer LBW_BOWLED = 8 ;
        public final static Integer THREE_WICKET_HALL = 4 ;
        public final static Integer FOUR_WICKET_HALL = 8 ;
        public final static Integer FIVE_WICKET_HALL = 16 ;
    }

    public final class Fielding
    {
        public final static Integer CATCH = 8 ;
        public final static Integer THREE_CATCH_BONUS = 4 ;
        public final static Integer STUMPING = 12 ;
        public final static Integer RUN_OUT_DIRECT = 12 ;
        public final static Integer RUN_OUT = 6 ;
    }

    public final class others
    {
        public final static Integer CAPTAIN = 2 ;
        public final static Double VICE_CAPTAIN = 1.5 ;
        public final static Double IN_PLAYING11 = 4.0 ;
    }  
    
    public final class strikerate
    {
        public final static Integer BELOW_50 = -6 ;
        public final static Integer BETWEEN_50_5999 = -4 ;
        public final static Integer BETWEEN_60_6999 = -2 ;
        public final static Integer BETWEEN_70_12999 = 0 ;
        public final static Integer BETWEEN_130_14999 = 2 ;
        public final static Integer BETWEEN_150_16999 = 4 ;
        public final static Integer ABOVE_170 = 6 ;
    }

    public final class economy
    {
        public final static Integer BELOW_5 = 6 ;
        public final static Integer BETWEEN_5_599 = 4 ;
        public final static Integer BETWEEN_6_699 = 2 ;
        public final static Integer BETWEEN_7_999 = 0 ;
        public final static Integer BETWEEN_10_1099 = -2 ;
        public final static Integer BETWEEN_11_1199 = -4 ;
        public final static Integer ABOVE_12 = -6 ;
    }
}
