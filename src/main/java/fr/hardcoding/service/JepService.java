package fr.hardcoding.service;

import static fr.hardcoding.model.JepState.SUBMITTED;
import static java.util.Objects.requireNonNull;

import fr.hardcoding.model.Jep;
import fr.hardcoding.model.JepState;
import fr.hardcoding.model.JepType;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JepService {
  private static final Logger LOG = LoggerFactory.getLogger(JepService.class);
  private static final String JEP_URL = "https://openjdk.org/jeps/0";

  private final BlueskyService blueskyService;

  public JepService(BlueskyService blueskyService) {
    this.blueskyService = blueskyService;
  }

  @Scheduled(every = "1h")
  @Transactional
  public void checkJepUpdates() {
    List<Jep> currentJeps;
    List<Jep> updatedJeps = new ArrayList<>();
    try {
      currentJeps = fetchJeps();
    } catch (IOException e) {
      LOG.error("Error fetching JEPs", e);
      return;
    }

    for (Jep currentJep : currentJeps) {
      // Skip submitted only JEPs, no JEP number
      if (currentJep.state == SUBMITTED) {
        continue;
      }
      Jep existingJep = Jep.findByNumber(currentJep.number);

      if (existingJep == null) {
        // New JEP
        currentJep.persist();
        updatedJeps.add(currentJep);
      } else if (!existingJep.state.equals(currentJep.state)) {
        // Status changed
        existingJep.state = currentJep.state;
        existingJep.persist();
        updatedJeps.add(existingJep);
      }
    }

    // Post updates to Bluesky
    for (Jep updatedJep : updatedJeps) {
      LOG.info("Updating Jep {} with status {}", updatedJep.number, updatedJep.state);
      String message = formatJepUpdate(updatedJep);
      LOG.info(message);
//                blueskyService.postUpdate(message);
    }
  }

  private List<Jep> fetchJeps() throws IOException {
    LOG.info("Fetching JEPs");
    Document doc = Jsoup.connect(JEP_URL).get();
    Elements rows = doc.select("table.jeps tr");
    List<Jep> jeps = new ArrayList<>();

    for (Element row : rows) {
      Elements cells = row.select("td");
      if (cells.size() >= 5) {
        Jep jep = new Jep();
        jep.type = JepType.fromShortName(cells.get(0).text());
        jep.state = JepState.fromShortName(cells.get(1).text());
        jep.release = valueOrNull(cells.get(2).text());
        jep.component = componentOrNull(cells.select(".cl").text());
        jep.subComponent = componentOrNull(cells.select(".cr").text());
        jep.number = valueOrNull(cells.select(".jep").text());
        jep.title = valueOrNull(requireNonNull(cells.last()).text());
        jeps.add(jep);
        LOG.debug("Found {}", jep);
      }
    }

    return jeps;
  }

  private String valueOrNull(String s) {
    return s.isBlank() ? null : s;
  }

  private String componentOrNull(String s) {
    String value = valueOrNull(s);
    return switch (value) {
      case null -> null;
      case "—" -> null;
      default -> value;
    };
  }

  private String formatJepUpdate(Jep jep) {
    String status = switch (jep.state) {
      case DRAFTED -> "✏️ JEP "+jep.number+" was drafted";
      case SUBMITTED -> "🗳️ JEP "+jep.number+" was submitted";
      case CANDIDATE -> "🎓 JEP "+jep.number+" moved to candidate";
      case PROPOSED_TO_TARGET -> "🎯 JEP "+jep.number+" proposed to target JDK "+jep.release;
      case TARGETED -> "🎯 JEP "+jep.number+" updated to target JDK "+jep.release;
      case INTEGRATED -> "🏗️ JEP "+jep.number+" integrated to JDK "+jep.release;
      case CLOSED_DELIVERED -> {
        if (jep.release == null) {
          yield "🪦 JEP "+jep.number+" was withdrawn";
        } else {
          yield  "📦 JEP "+jep.number+" delivered to JDK "+jep.release;
        }
      }
      case ACTIVE -> "JEP "+jep.number+" is now active";
    };
    String component = "";
    if (jep.component != null) {
      component = "\nComponent: " + jep.component;
      if (jep.subComponent != null) {
        component += " / " + jep.subComponent;
      }
    }
    String release = "";
    if (jep.release != null) {
      release = "\nRelease: " + jep.release;
    }
    return String.format("""
        %s
        Title: %s
        Type: %s%s%s
        See openjdk.org/jeps/%s
        """,
        status, jep.title, jep.type, component, release, jep.number);
  }
} 