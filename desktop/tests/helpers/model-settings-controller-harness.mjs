import { createModelSettingsController } from '../../src/model-settings-controller.mjs';

export function attachModelSettingsController(context) {
  context.modelSettingsController = createModelSettingsController({
    native: context.native !== false, currentOwner: () => context.state,
    render: () => context.render?.(),
    invoke: (owner, command, args) => context.invokeForAuthorizedDataAreaOperation
      ? context.invokeForAuthorizedDataAreaOperation(context.tauriBridge, command, args, owner)
      : context.invoke(command, args, owner),
    updateStatus: owner => { owner.status = context.modelServiceConfigurationStatus?.(owner.modelServiceSettings); },
  });
  context.clearOwnedModelCredentialDraft ||= owner => {
    owner.modelCredentialDraft = ''; owner.modelCredentialEdited = false; owner.modelCredentialVisible = false;
  };
}
