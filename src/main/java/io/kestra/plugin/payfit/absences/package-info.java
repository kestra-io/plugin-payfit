@PluginSubGroup(
    title = "PayFit Absences",
    description = "List, create, and cancel PayFit absences, and poll for new absences. Reading requires `time:read`. Writing requires `time:write`. Absences cannot be updated; cancel the existing absence and create a new one.",
    categories = {PluginSubGroup.PluginCategory.BUSINESS}
)
package io.kestra.plugin.payfit.absences;

import io.kestra.core.models.annotations.PluginSubGroup;
