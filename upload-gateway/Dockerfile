FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY main.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/nanfeng-attachment-gateway .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/nanfeng-attachment-gateway /nanfeng-attachment-gateway
ENV NANFENG_ATTACHMENT_GATEWAY_LISTEN=:8080
USER nonroot:nonroot
ENTRYPOINT ["/nanfeng-attachment-gateway"]
