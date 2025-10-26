package io.github.cepeppe.rest;


import io.github.cepeppe.http.HttpRestClient;
import lombok.Getter;

public abstract class AlpacaRestService {

    @Getter
    protected final AlpacaRestConfig alpacaRestConfig;

    protected final HttpRestClient httpRestClient;

    public AlpacaRestService(AlpacaRestBaseEndpoints desiredEndpoint){
        this.alpacaRestConfig = AlpacaRestConfig.fromEnvOrDefaultAlpacaRestConfig(desiredEndpoint);
        this.httpRestClient = new HttpRestClient();
    }
}
