#!/usr/bin/env bats

@test "health-check.sh requires environment argument" {
    run ./scripts/health-check.sh
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage"* ]]
}

@test "health-check.sh rejects unknown environment" {
    run ./scripts/health-check.sh invalid
    [ "$status" -eq 1 ]
    [[ "$output" == *"Unknown environment"* ]]
}
