{ pkgs ? import <nixpkgs> {} }:

let
  clojure = pkgs.clojure;
  inherit (clojure) jdk;
in
pkgs.mkShell {
  buildInputs = builtins.attrValues {
    inherit (pkgs) clojure-lsp imagemagick nodejs yarn;
    inherit clojure jdk;
  };
  JAVA_HOME = jdk.home;
}
