// Build/packaging selection only; no runtime data roots or credentials.
export function alignmentProfile(args = []) {
  if (args.length > 1 || (args.length && !['--profile=debug', '--profile=release'].includes(args[0]))) {
    throw new Error('Windows对齐打包profile参数无效；只允许一次debug或release选择。');
  }
  return args[0] === '--profile=release' ? 'release' : 'debug';
}

export function alignmentCandidateNamespace(profile) {
  if (!['debug', 'release'].includes(profile)) throw new Error('Windows候选profile无效。');
  return profile === 'release' ? 'windows-acceptance-release' : 'windows-acceptance';
}
