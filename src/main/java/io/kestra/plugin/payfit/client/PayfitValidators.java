package io.kestra.plugin.payfit.client;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PayfitValidators {
    public static final int MAX_PAGE_SIZE = 50;
    private static final Pattern ACCOUNTING_PERIOD = Pattern.compile("^2\\d{3}(0[1-9]|1[0-2])$");
    private static final List<String> ABSENCE_MOMENTS = List.of("beginning-of-day", "middle-of-day", "end-of-day");
    private static final Set<String> ABSENCE_TYPES = Set.of(
        "fr_conges_payes", "fr_rtt", "fr_repos", "fr_sans_solde", "fr_teletravail", "fr_ecole",
        "fr_maladie_ordinaire", "fr_mariage_salarie", "fr_pacs_salarie", "fr_mariage_enfant",
        "fr_mariage_frere_ou_soeur", "fr_mariage_beau_frere_ou_belle_soeur", "fr_mariage_ascendant",
        "fr_mariage_petit_enfant", "fr_deces_beau_pere_ou_belle_mere", "fr_deces_conjoint",
        "fr_deces_enfant", "fr_deces_frere_ou_soeur", "fr_deces_grand_parent", "fr_deces_pere_ou_mere",
        "fr_deces_beau_frere_ou_belle_soeur", "fr_deces_gendre_ou_belle_fille", "fr_deces_petit_enfant",
        "fr_demenagement", "fr_echographie_prenatale", "fr_conjoint_malade_ou_hospitalise",
        "fr_handicap_enfant", "fr_examen_professionnel_formation_continue", "fr_appel_preparation_defense",
        "fr_ceremonie_enfant_communion_solennelle", "fr_demarches_adoption",
        "es_huelga", "es_permiso_por_convenio_no_remunerado", "es_permiso_parental", "es_vacaciones",
        "es_teletrabajo", "es_descanso_horas_extras", "es_descanso_por_festivo_trabajado", "es_visita_medica",
        "es_permiso_por_matrimonio", "es_deber_inexcusable", "es_examenes_prenatales",
        "es_tecnicas_de_preparacion_al_parto", "es_adopcion", "es_mudanza", "es_permiso_por_examenes",
        "es_permiso_por_formacion", "es_asuntos_propios", "es_jornada_de_licencia", "es_lactancia_acumulada",
        "es_prematuros_hospitalizados", "es_permiso_especial_fuerza_mayor", "es_otro_asunto_personal",
        "es_fallecimiento_pariente_de_primer_grado_de_consanguinidad",
        "es_fallecimiento_pariente_de_segundo_grado_de_consanguinidad", "es_hospitalizacion_de_un_familiar",
        "es_intervencion_quirurgica_sin_hospitalizacion", "es_accidente_o_enfermedad_grave",
        "es_permiso_asistencia_a_matrimonio", "es_fallecimiento_pariente_de_tercer_grado_de_consanguinidad",
        "es_otro_asunto_familiar", "es_permiso_por_horas",
        "uk_annual_leave", "uk_remote", "uk_sick_leave", "uk_unpaid_leave", "uk_compassionate_leave",
        "uk_custom_leave_1", "uk_custom_leave_2", "uk_custom_leave_3", "uk_custom_leave_4", "uk_custom_leave_5"
    );
    private static final Set<String> ABSENCE_STATUSES = Set.of(
        "approved",
        "pending_approval",
        "declined",
        "cancelled",
        "pending_cancellation",
        "all"
    );
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE");

    private PayfitValidators() {
    }

    public static void pageSize(Integer maxResults) {
        if (maxResults == null) {
            return;
        }
        if (maxResults < 1 || maxResults > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("maxResults must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static void maxPages(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be at least 1");
        }
    }

    public static void requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    public static String isoDate(String value, String name) {
        requiredText(value, name);
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 date (YYYY-MM-DD)");
        }
    }

    public static String accountingPeriod(String value) {
        requiredText(value, "date");
        if (!ACCOUNTING_PERIOD.matcher(value).matches()) {
            throw new IllegalArgumentException("date must match PayFit accounting period format YYYYMM, with a year from 2000 onward");
        }
        return value;
    }

    public static String absenceType(String value) {
        requiredText(value, "absenceType");
        if (!ABSENCE_TYPES.contains(value)) {
            throw new IllegalArgumentException("absenceType is not a PayFit absence type");
        }
        return value;
    }

    public static String absenceMoment(String value, String name, String defaultValue) {
        String moment = value == null || value.isBlank() ? defaultValue : value.trim();
        if (!ABSENCE_MOMENTS.contains(moment)) {
            throw new IllegalArgumentException(name + " must be beginning-of-day, middle-of-day, or end-of-day");
        }
        return moment;
    }

    public static void absenceRange(String startDate, String startMoment, String endDate, String endMoment) {
        int dateCompare = endDate.compareTo(startDate);
        if (dateCompare < 0 || (dateCompare == 0 && ABSENCE_MOMENTS.indexOf(endMoment) < ABSENCE_MOMENTS.indexOf(startMoment))) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }

    public static String absenceStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!ABSENCE_STATUSES.contains(normalized)) {
                throw new IllegalArgumentException(
                    "status must be one of " + ABSENCE_STATUSES + " or a comma-separated combination, but got '" + token.trim() + "'"
                );
            }
            tokens.add(normalized);
        }
        if (tokens.isEmpty()) {
            return null;
        }
        if (tokens.contains("all") && tokens.size() > 1) {
            throw new IllegalArgumentException("status 'all' cannot be combined with other statuses");
        }
        return String.join(",", tokens);
    }

    public static String gender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!GENDERS.contains(normalized)) {
            throw new IllegalArgumentException("gender must be MALE or FEMALE");
        }
        return normalized;
    }

    public static Integer children(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 20) {
            throw new IllegalArgumentException("numberOfChildren must be an integer between 0 and 20");
        }
        return value;
    }

    public static String email(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1 || trimmed.contains(" ")) {
            throw new IllegalArgumentException(name + " must be an email address");
        }
        return trimmed;
    }
}
