import type { FastifyInstance } from "fastify";
import {
  conversationRequest,
  LocalConversationProvider,
  type ConversationProvider,
} from "../russian/conversation.js";

export async function registerRussianConversationRoutes(
  app: FastifyInstance,
  provider: ConversationProvider = new LocalConversationProvider(),
) {
  let pending = 0;
  app.get("/russian/conversation/status", () => provider.status());
  app.post("/russian/conversation/turn", { bodyLimit: 160_000 }, async (req, reply) => {
    const parsed = conversationRequest.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_conversation" });
    if (pending >= 2) return reply.code(429).send({ error: "conversation_busy" });
    pending++;
    try {
      return await provider.respond(parsed.data);
    } catch {
      // No learner transcripts or upstream response bodies in logs.
      return reply.code(503).send({ error: "conversation_unavailable" });
    } finally {
      pending--;
    }
  });
}
