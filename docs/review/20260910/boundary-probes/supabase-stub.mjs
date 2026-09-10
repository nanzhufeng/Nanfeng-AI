export function createClient() {
  return { auth: { getUser: async () => ({ data: { user: { identities: [
    { provider: "google", identity_data: { picture: "https://fixture.googleusercontent.com/avatar.png" } }
  ] } }, error: null }) } };
}
