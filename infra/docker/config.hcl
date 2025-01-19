# Enable dev mode
ui = true

backend "consul" {
  address = "consul:8500"
  path = "vault/"
}
