import React, { Component, ReactNode } from 'react'
import { Button, Dialog, DialogActions, DialogContent, TextField } from '@material-ui/core'

type State = {
  open: boolean
  value: number
}

export default class UpdateSprinklingDialog extends Component<{}, State> {

  state: State = {
    open: false,
    value: 0
  }

  resolve?: (value?: number | PromiseLike<number> | undefined) => void = undefined

  open = (value: number) => {
    this.setState({
      open: true,
      value
    })
    return new Promise<number>((resolve, reject) => {
      this.resolve = resolve
    })
  }

  render() {
    const { open, value } = this.state
    return(
      <Dialog
        open={open}
        onClose={() => this.setState({ open: false })}
      >
        <DialogContent>
          <TextField
            type="number"
            autoFocus
            fullWidth
            label="Beregening in mm"
            value={value}
            inputProps={{
              min: 0
            }}
            onChange={e => this.setState({ value: parseInt(e.target.value, 10)})}
          />
        </DialogContent>
        <DialogActions>
          <Button
            color="primary"
            onClick={() => {
              this.setState({ open: false })
              this.resolve!(value)
            }}
          >Bijwerken</Button>
          <Button
            onClick={() => this.setState({ open: false })}
          >Annuleren</Button>
        </DialogActions>
      </Dialog>
    )
  }
}
