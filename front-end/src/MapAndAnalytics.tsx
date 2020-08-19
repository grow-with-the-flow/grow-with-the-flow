import React, { useState, useEffect } from 'react'
import { RouteComponentProps, Redirect } from 'react-router-dom'
import axios from 'axios'
import { css } from 'emotion'
import { Paper, CircularProgress } from '@material-ui/core'
import { fromPairs } from 'lodash'

import MapView from './MapView'
import Analytics from './Analytics';
import OverallSummary from './OverallSummary';

type Props = RouteComponentProps<{
  date?: string
  selectionType?: 'plot' | 'pixel'
  selectionId?: string
}>

export default ({ match, history }: Props) => {

  const [ farmerData, setFarmerData ] = useState(null as any)
  const [ sprinklingCache, setSprinklingCache ] = useState({})

  useEffect(() => {
    (async () => {

      const orderedLandUses = [
        'gras',
        'mais',
        'aardappelen',
        'bieten',
        'granen',
        'overige landbouwgew',
        'boomteelt',
        'glastuinbouw',
        'boomgaard',
        'bollen',
        'loofbos',
        'naaldbos',
        'natte natuur',
        'droge natuur',
        'kale grond',
        'zoet water',
        'zout water',
        'stedelijk bebouwd',
        'donker naaldbos'
        
      ]

      //const prefix = process.env.NODE_ENV === 'development' ? '/data' : 'https://storage.googleapis.com/grow-with-the-flow.appspot.com'
      const prefix = 'https://storage.googleapis.com/grow-with-the-flow.appspot.com'

      const { defaultDate } = (await axios.get(`${prefix}/defaults.json`)).data
      const dateToken = defaultDate.replace(/-/g, '')

      const d: any = fromPairs(await Promise.all([
        ['landUse', 'gwtf-land-use.json'],
        ['soilMap', 'gwtf-soil-map.json'],
        ['pixelsData', `gwtf-pixels-${dateToken}.json`],
        ['plotsAnalytics', `gwtf-plot-analytics-${dateToken}.json`],
        ['plotsGeoJSON', `gwtf-plots-${dateToken}.json`]
      ].map(async ([key, path]: any) => ([key, (await axios.get(`${prefix}/${path}`)).data]))))

      d.pixelsData.landUse = d.landUse
      d.pixelsData.soilMap = d.soilMap

      const { pixelsData, plotsAnalytics, plotsGeoJSON } = d
      plotsGeoJSON.features = plotsGeoJSON.features.filter((f: any) => f.properties.plotId)

      const farmerData = {
        defaultDate,
        pixelsData,
        plotsGeoJSON,
        plotsAnalytics
      }

      setFarmerData(farmerData)
    })()
  }, [])

  if(!farmerData) {
    return(
      <div
        className={css`
          height: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
        `}
      >
        <CircularProgress/>
      </div>
    )
  }

  const { date, selectionType, selectionId } = match.params
  
  const { defaultDate } = farmerData
  if(!date || date !== defaultDate) {
    return <Redirect to={`/map/${defaultDate}`}/>
  }

  let selectedPlotId: string | undefined = undefined
  let selectedPixel: Array<number> | undefined = undefined

  if(selectionId) {
    switch(selectionType) {
      case 'plot':
        selectedPlotId = selectionId
        break
      case 'pixel':
        selectedPixel = selectionId.split('-').map(n => parseInt(n, 10))
        break
      default:
        return <Redirect to={`/map/${date}`}/>
    }
  }

  const navigate = (path: string) => history.push(path)

  return(
    <div
      className={css`
        height: 100%;
        display: flex;
        flex-direction: column;
      `}
    >
      <div
        className={css`
          position: relative;
          flex: 1;
        `}
      >
        <MapView
          navigate={navigate}
          farmerData={farmerData}
          date={date}
          selectedPlotId={selectedPlotId}
          selectedPixel={selectedPixel}
        />
      </div>
      <Paper
        elevation={5}
        className={css`
          position: relative;
          z-index: 1000;
        `}
        square
      >
        {(selectedPlotId || selectedPixel) ?
          <Analytics
            date={new Date(date)}
            farmerData={farmerData}
            navigate={navigate}
            selectedPixel={selectedPixel}
            selectedPlotId={selectedPlotId}
            sprinklingCache={sprinklingCache}
            setSprinklingCache={setSprinklingCache}
          />
          :
          <OverallSummary
            date={new Date(date)}
            farmerData={farmerData}
            navigate={navigate}
            sprinklingCache={sprinklingCache}
          />
        }
      </Paper>
    </div>
  )
}