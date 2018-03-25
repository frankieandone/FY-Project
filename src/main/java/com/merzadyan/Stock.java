package com.merzadyan;

import java.util.Comparator;

public class Stock implements Comparator {
    private String company;
    private String symbol;
    private String stockExchange;
    
    public Stock() {
    
    }
    
    public Stock(String company, String symbol, String stockExchange) {
        this.company = company;
        this.symbol = symbol;
        this.stockExchange = stockExchange;
    }
    
    @Override
    public int compare(Object o1, Object o2) {
        Stock s1 = (Stock) o1, s2 = (Stock) o2;
        
        return s1.getCompany().compareToIgnoreCase(s2.getCompany());
    }
    
    @Override
    public boolean equals(Object object) {
        if (object == null || object instanceof Stock) {
            return false;
        }
        
        return this.symbol.equals(((Stock) object).symbol);
    }
    
    public String getCompany() {
        return company;
    }
    
    public void setCompany(String company) {
        if (company != null) {
            this.company = company.trim();
            return;
        }
        this.company = null;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        if (symbol != null) {
            this.symbol = symbol.trim();
            return;
        }
        this.symbol = null;
    }
    
    public String getStockExchange() {
        return stockExchange;
    }
    
    public void setStockExchange(String stockExchange) {
        if (stockExchange != null) {
            this.stockExchange = stockExchange.trim();
            return;
        }
        this.stockExchange = null;
    }
}
