var d3 = {};

(function() {
    d3.legend = function(g) {
	console.log(g);
  g.each(function() {
    var g= d3.select(this),
        items = {},
        svg = d3.select(g.property("nearestViewportElement")),
        legendPadding = g.attr("data-style-padding") || 5,
        lb = g.selectAll(".legend-box").data([true]),
        li = g.selectAll(".legend-items").data([true])

    lb.enter().append("rect").classed("legend-box",true)
    lig = li.enter().append("g").classed("legend-items",true)

      svg.selectAll("[data-legend]").each(function() {
	  console.log("Found item " + this);
        var self = d3.select(this)
        items[self.attr("data-legend")] = {
          pos : self.attr("data-legend-pos") || this.getBBox().y,
          color : self.attr("data-legend-color") != undefined ? self.attr("data-legend-color") : self.style("fill") != 'none' ? self.style("fill") : self.style("stroke") 
        }
      })

    items = d3.entries(items).sort(function(a,b) { return a.value.pos-b.value.pos})
      console.log("Items")
      console.log(items)

      console.log("Lig ")
      console.log(lig)
      
    lig.selectAll("text")
          .data(items,function(d) { return d.key})
	  .enter().append("text")
//        .call(function(d) { d.enter().append("text")})
        .attr("y",function(d,i) { return i+"em"})
        .attr("x","1em")
          .text(function(d) { ;return d.key})
	  .exit().remove();
//        .call(function(d) { d.exit().remove()})
    
    lig.selectAll("circle")
          .data(items,function(d) { return d.key})
	  .enter().append("circle")
      //        .call(function(d) { d.enter().append("circle")})
        .attr("cy",function(d,i) { return i-0.25+"em"})
        .attr("cx",0)
        .attr("r","0.4em")
        .style("fill",function(d) { console.log(d.value.color);return d.value.color})  
      //        .call(function(d) { d.exit().remove()})
	  .exit().remove()

      linodes = lig.node(); //g.selectAll(".legend-items").nodes();
      console.log("linodes")
      console.log(linodes)
      
    // Reposition and resize the box
      var lbbox = linodes.getBBox()
      console.log("lbbox")
      console.log(lbbox)
    lb.attr("x",(lbbox.x-legendPadding))
        .attr("y",(lbbox.y-legendPadding))
        .attr("height",(lbbox.height+2*legendPadding))
        .attr("width",(lbbox.width+2*legendPadding))
  })
  return g
}
})()
