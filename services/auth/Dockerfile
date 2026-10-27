FROM golang:1.27.1-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /plotus-auth ./cmd/auth

FROM alpine:3.23
RUN apk add --no-cache ca-certificates && addgroup -g 10001 auth && adduser -D -u 10001 -G auth auth
COPY --from=build /plotus-auth /usr/local/bin/plotus-auth
USER auth
EXPOSE 4001
CMD ["plotus-auth"]
