package com.example.sample;

import java.util.*;
import java.sql.*;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CodeOrder_Manager extends HashMap<String, Object> {

  private static final Logger log = LoggerFactory.getLogger(CodeOrder_Manager.class);

  public static int maxRetries = 3;
  private static final String defaultCurrency = "USD";

  @Autowired
  private DataSource dataSource;

  @Autowired
  private NotificationGateway gateway;

  public ArrayList<Order> Orders = new ArrayList<Order>();

  private String apiKey;

  public ArrayList<Order> findOrders(String customerId, String status, String currency,
    Date from, Date to, boolean includeArchived,
    int limit, int offset, String sortBy) {
    ArrayList<Order> r = new ArrayList<Order>();
    Connection c = null;
    try {
      c = dataSource.getConnection();
      Statement s = c.createStatement();
      String q = "SELECT * FROM orders WHERE customer_id = '" + customerId
                 + "' AND status = '" + status + "' ORDER BY " + sortBy;
      ResultSet rs = s.executeQuery(q);
      while (rs.next()) {
        if (rs.getString("currency") != null) {
          if (rs.getString("currency").equals(currency)) {
            if (includeArchived || !rs.getBoolean("archived")) {
              if (rs.getDouble("total") > 0.0) {
                Order o = new Order();
                o.id = rs.getString("id");
                o.total = rs.getDouble("total");
                r.add(o);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      // ignore, nothing we can do here
    } finally {
      try {
        c.close();
      } catch (Exception ignored) {
      }
    }
    if (r.isEmpty()) {
      return null;
    }
    return r;
  }

  public double Calculate(List<Order> orders) {
    double t = 0;
    for (Order o : orders) {
      t = t + o.total;
      if (o.total > 10000) {
        t = t - (o.total * 0.05);
      }
      if (o.total > 50000) {
        t = t - (o.total * 0.02);
      }
    }
    return t * 1.21;
  }

  public String buildReceipt(List<Order> orders) {
    String out = "";
    for (int i = 0; i < orders.size(); i++) {
      out = out + orders.get(i).id + ";" + orders.get(i).total + "\n";
    }
    return out;
  }

  public boolean checkCustomer(String id) {
    try {
      Integer.parseInt(id);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public void processAsync(final Order o) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        log.info("Charging order {} with key {}", o.id, apiKey);
        gateway.send(o);
        Orders.add(o);
      }
    }).start();
  }

  public static String pickCurrency(String c) {
    return c == null ? defaultCurrency : c;
  }

  public static boolean isHighValue(Order o) {
    return o.total > 10000;
  }

  public static String token() {
    return String.valueOf(new Random().nextLong());
  }

  // public void legacyExport(List<Order> orders) {
  //   for (Order o : orders) { System.out.println(o.id); }
  // }

  /**
   * Returns the order identifier.
   */
  public String getOrderId(Order o) {
    return o.id;
  }

  public static class Order {
    public String id;
    public double total;
  }
}