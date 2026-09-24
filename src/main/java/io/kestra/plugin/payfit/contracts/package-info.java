@PluginSubGroup(
    title = "PayFit Contracts",
    description = "List, fetch, and initialize PayFit employment contracts. Listing requires `contracts:read`. Creating a contract is currently limited to French companies and requires `collaborators:contracts:write`.",
    categories = {PluginSubGroup.PluginCategory.DATA}
)
package io.kestra.plugin.payfit.contracts;

import io.kestra.core.models.annotations.PluginSubGroup;
