package ma.jurika.dataroom.infrastructure.messaging;

import ma.jurika.dataroom.application.DeleteDataroomUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E16a (reponse E1 n° 4) : l'annulation d'un ticket ne supprime
 * plus la Data Room (RG-TKT-05 : un ticket annule peut etre repris ; RG-DR-05 :
 * aucun document invisible). Aucun composant de dataroom n'ecoute plus la file
 * d'annulation, et aucun composant de messagerie ne depend du cas d'usage de
 * suppression.
 */
class AnnulationTicketSansSuppressionTest {

    private static final String FILE_ANNULATION = "dataroom.ticket-annulation";

    private static List<Class<?>> composants() throws ClassNotFoundException {
        var scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition def : scan.findCandidateComponents("ma.jurika.dataroom")) {
            classes.add(Class.forName(def.getBeanClassName()));
        }
        assertThat(classes).as("le scan doit trouver des composants").isNotEmpty();
        return classes;
    }

    @Test
    void aucun_ecouteur_sur_la_file_d_annulation() throws Exception {
        List<String> ecouteurs = new ArrayList<>();
        for (Class<?> c : composants()) {
            for (Method m : c.getDeclaredMethods()) {
                RabbitListener l = m.getAnnotation(RabbitListener.class);
                if (l != null && Arrays.asList(l.queues()).contains(FILE_ANNULATION)) {
                    ecouteurs.add(c.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(ecouteurs).isEmpty();
    }

    @Test
    void la_messagerie_ne_depend_pas_de_la_suppression_de_data_room() throws Exception {
        List<String> dependants = new ArrayList<>();
        for (Class<?> c : composants()) {
            if (!c.getPackageName().contains(".messaging")) continue;
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == DeleteDataroomUseCase.class) dependants.add(c.getSimpleName());
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (Arrays.asList(k.getParameterTypes()).contains(DeleteDataroomUseCase.class)) {
                    dependants.add(c.getSimpleName());
                }
            }
        }
        assertThat(dependants).isEmpty();
    }
}
