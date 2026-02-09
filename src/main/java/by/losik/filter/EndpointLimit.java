package by.losik.filter;

public class EndpointLimit {
    private final int maxRequestsPerMinute;
    private final int maxRequestsPerHour;
    private final int maxRequestsPerDay;

    public EndpointLimit(int perMinute, int perHour, int perDay) {
        this.maxRequestsPerMinute = perMinute;
        this.maxRequestsPerHour = perHour;
        this.maxRequestsPerDay = perDay;
    }

    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
    public int getMaxRequestsPerHour() { return maxRequestsPerHour; }
    public int getMaxRequestsPerDay() { return maxRequestsPerDay; }
}