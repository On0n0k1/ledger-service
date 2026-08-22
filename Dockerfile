FROM clojure:temurin-21-tools-deps-bookworm-slim

WORKDIR /app

COPY deps.edn ./
RUN clojure -P

COPY src ./src

EXPOSE 3000

CMD ["clojure", "-M:run"]
