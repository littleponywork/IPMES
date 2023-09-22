pub mod darpa;
pub mod order_relation;
pub mod spade;
pub mod parser;

use order_relation::OrderRelation;

use clap::ValueEnum;
use std::io::BufRead;

pub struct Edge {
    id: usize,
    signature: String,
    start: usize,
    end: usize,
}

pub struct Pattern {
    pub edges: Vec<Edge>,
    pub order: OrderRelation,
}