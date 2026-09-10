export function createClient(..._args) {
  return { auth: { getUser: async (..._args) => ({ data: { user: { identities: [
    { provider: "google", identity_data: { picture: "https://fixture.googleusercontent.com/avatar.png" } }
  ] } }, error: null }) } };
}
